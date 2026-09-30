package com.srmasset.creditengine.settlement;

import com.srmasset.creditengine.fx.ExchangeRateRow;
import com.srmasset.creditengine.fx.ExchangeRateService;
import com.srmasset.creditengine.observability.SettlementMetrics;
import com.srmasset.creditengine.pricing.Currency;
import com.srmasset.creditengine.pricing.FxRate;
import com.srmasset.creditengine.pricing.Money;
import com.srmasset.creditengine.pricing.MonthlyRate;
import com.srmasset.creditengine.pricing.PricingContext;
import com.srmasset.creditengine.pricing.PricingEngine;
import com.srmasset.creditengine.pricing.PricingRequest;
import com.srmasset.creditengine.pricing.PricingResult;
import com.srmasset.creditengine.pricing.RoundingPolicy;
import com.srmasset.creditengine.pricing.StrategyRegistry;
import com.srmasset.creditengine.pricing.TermCalculator;
import com.srmasset.creditengine.rates.BaseRate;
import com.srmasset.creditengine.rates.BaseRateRepository;
import com.srmasset.creditengine.rates.CurrencyRepository;
import com.srmasset.creditengine.receivable.ReceivableRepository;
import com.srmasset.creditengine.receivable.ReceivableRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Liquidação (4.1.3): transação CURTA cobrindo apenas UPDATE versionado -> INSERT do
 * snapshot. Todo I/O de resolução (taxa base, câmbio, prazo, precificação) acontece ANTES
 * do BEGIN — o provedor de câmbio jamais é chamado aqui (lê-se a tabela interna), então a
 * queda dele vira 503 sem nada escrito, nunca uma liquidação "pela metade".
 *
 * <p>Defesa em profundidade contra duplicidade: (1) checagem de status na leitura,
 * (2) lock otimista na versão do recebível, (3) UNIQUE(receivable_id) e
 * (4) UNIQUE(idempotency_key) no banco — a última linha vale mesmo sem a aplicação.
 */
@Service
public class SettlementService {

    private final SettlementRepository settlements;
    private final ReceivableRepository receivables;
    private final CurrencyRepository currencies;
    private final BaseRateRepository baseRates;
    private final ExchangeRateService fx;
    private final PricingEngine engine;
    private final StrategyRegistry registry;
    private final RoundingPolicy rounding;
    private final SettlementHooks hooks;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final SettlementMetrics metrics;

    public SettlementService(SettlementRepository settlements, ReceivableRepository receivables,
            CurrencyRepository currencies, BaseRateRepository baseRates, ExchangeRateService fx,
            PricingEngine engine, StrategyRegistry registry, RoundingPolicy rounding,
            SettlementHooks hooks, Clock clock, PlatformTransactionManager txManager,
            SettlementMetrics metrics) {
        this.settlements = settlements;
        this.receivables = receivables;
        this.currencies = currencies;
        this.baseRates = baseRates;
        this.fx = fx;
        this.engine = engine;
        this.registry = registry;
        this.rounding = rounding;
        this.hooks = hooks;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.metrics = metrics;
    }

    public SettlementOutcome settle(SettleCommand cmd) {
        String hash = requestHash(cmd);

        // 1. replay antes de qualquer coisa: retry legitimo nao depende do estado atual
        Optional<SettlementRow> existing = settlements.findByIdempotencyKey(cmd.idempotencyKey());
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), hash);
        }

        // 2. leitura do recebivel (a versao lida participa do lock otimista)
        ReceivableRow receivable = receivables.findById(cmd.receivableId())
                .orElseThrow(() -> new ReceivableNotFoundException(cmd.receivableId()));
        if (!"OPEN".equals(receivable.status())) {
            throw new AlreadySettledException(receivable.id());
        }

        // 3. resolucoes e calculo FORA da transacao.
        // Truncado a micros: timestamptz do PostgreSQL guarda microssegundos, e o replay
        // (lido do banco) deve ser byte a byte identico a resposta original (em memoria).
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        LocalDate today = LocalDate.now(clock);
        BaseRate baseRate = baseRates.asOf(now)
                .orElseThrow(() -> new IllegalStateException("Sem taxa base vigente"));
        Currency payment = currencies.find(receivable.paymentCurrency())
                .orElseThrow(() -> new IllegalStateException(
                        "Moeda cadastrada desconhecida: " + receivable.paymentCurrency()));

        ExchangeRateRow fxRow = null;
        FxRate fxRate = null;
        if (!Currency.BRL.equals(payment)) {
            fxRow = fx.current(payment.code(), Currency.BRL.code(), now);
            fxRate = new FxRate(payment, Currency.BRL, fxRow.rate());
        }

        int termMonths = TermCalculator.termMonths(today, receivable.dueDate());
        PricingContext ctx = new PricingContext(new MonthlyRate(baseRate.monthlyRate()), rounding);
        FxRate fxForPricing = fxRate;
        PricingResult result = metrics.timePricing(() -> engine.price(new PricingRequest(
                receivable.type(), new Money(receivable.faceValue(), Currency.BRL),
                termMonths, payment, fxForPricing), ctx));

        if (cmd.expectedAmount() != null
                && !result.paidAmount().amount().toPlainString().equals(cmd.expectedAmount())) {
            throw new PriceChangedException(
                    cmd.expectedAmount(), result.paidAmount().amount().toPlainString());
        }

        hooks.afterPricing();

        SettlementRow toInsert = new SettlementRow(
                0, receivable.id(), receivable.cedenteId(), cmd.idempotencyKey(), hash,
                receivable.type(), receivable.faceValue(), termMonths, today,
                baseRate.id(), baseRate.monthlyRate(),
                registry.require(receivable.type()).spread().value().setScale(6),
                rounding.mode().name(),
                result.presentValueBrl().amount(), result.discountBrl().amount(),
                payment.code(), result.paidAmount().amount(),
                fxRow == null ? null : fxRow.id(),
                fxRow == null ? null : fxRow.rate(),
                fxRow == null ? null : fxRow.validFrom(),
                cmd.operator(), now);

        // 5. transacao curta: UPDATE versionado, depois INSERT (a ordem importa)
        try {
            SettlementRow saved = tx.execute(status -> {
                int updated = settlements.markSettled(receivable.id(), receivable.version());
                if (updated == 0) {
                    throw new StaleReceivableSignal();
                }
                long id = settlements.insert(toInsert);
                return withId(toInsert, id);
            });
            metrics.recordSettlement("created", payment.code());
            return new SettlementOutcome(saved, true);
        } catch (StaleReceivableSignal stale) {
            // outra transacao venceu a corrida: se foi a MESMA chave, e replay; senao, conflito
            Optional<SettlementRow> winner = settlements.findByIdempotencyKey(cmd.idempotencyKey());
            if (winner.isPresent()) {
                return replayOrReject(winner.get(), hash);
            }
            throw new VersionConflictException(receivable.id());
        } catch (DuplicateKeyException duplicate) {
            // ultima linha de defesa (23505): decide pelo indice violado
            if (SettlementRepository.isIdempotencyKeyViolation(duplicate)) {
                return replayOrReject(
                        settlements.findByIdempotencyKey(cmd.idempotencyKey()).orElseThrow(), hash);
            }
            throw new AlreadySettledException(receivable.id());
        }
    }

    private SettlementOutcome replayOrReject(SettlementRow row, String hash) {
        if (!row.requestHash().equals(hash)) {
            throw new IdempotencyKeyReuseException();
        }
        metrics.recordSettlement("replayed", row.paymentCurrency());
        return new SettlementOutcome(row, false);
    }

    private static String requestHash(SettleCommand cmd) {
        String canonical = cmd.receivableId() + "|"
                + (cmd.expectedAmount() == null ? "" : cmd.expectedAmount());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    private static SettlementRow withId(SettlementRow s, long id) {
        return new SettlementRow(id, s.receivableId(), s.cedenteId(), s.idempotencyKey(),
                s.requestHash(), s.strategy(), s.faceValue(), s.termMonths(), s.pricingDate(),
                s.baseRateId(), s.baseRate(), s.spread(), s.roundingMode(), s.presentValueBrl(),
                s.discountBrl(), s.paymentCurrency(), s.paidAmount(), s.fxRateId(), s.fxRate(),
                s.fxValidFrom(), s.settledBy(), s.settledAt());
    }

    /** Sinal interno de rowcount 0 no UPDATE versionado: forca rollback e decide fora da tx. */
    private static final class StaleReceivableSignal extends RuntimeException {
    }
}
