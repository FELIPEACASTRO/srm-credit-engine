package com.srmasset.creditengine.receivable;

import com.srmasset.creditengine.fx.ExchangeRateRow;
import com.srmasset.creditengine.fx.ExchangeRateService;
import com.srmasset.creditengine.pricing.Currency;
import com.srmasset.creditengine.pricing.FxRate;
import com.srmasset.creditengine.pricing.Money;
import com.srmasset.creditengine.pricing.PricingContext;
import com.srmasset.creditengine.pricing.PricingEngine;
import com.srmasset.creditengine.pricing.PricingRequest;
import com.srmasset.creditengine.pricing.PricingResult;
import com.srmasset.creditengine.pricing.RoundingPolicy;
import com.srmasset.creditengine.pricing.StrategyRegistry;
import com.srmasset.creditengine.pricing.TermCalculator;
import com.srmasset.creditengine.pricing.MonthlyRate;
import com.srmasset.creditengine.rates.BaseRate;
import com.srmasset.creditengine.rates.BaseRateRepository;
import com.srmasset.creditengine.rates.CurrencyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Cadastro idempotente e simulação (4.2.1). A simulação usa o MESMO PricingEngine da
 * liquidação — fonte única de verdade do cálculo; o frontend nunca calcula.
 */
@Service
public class ReceivableService {

    private final ReceivableRepository receivables;
    private final CurrencyRepository currencies;
    private final BaseRateRepository baseRates;
    private final ExchangeRateService fx;
    private final PricingEngine engine;
    private final StrategyRegistry registry;
    private final RoundingPolicy rounding;
    private final Clock clock;

    public ReceivableService(ReceivableRepository receivables, CurrencyRepository currencies,
            BaseRateRepository baseRates, ExchangeRateService fx, PricingEngine engine,
            StrategyRegistry registry, RoundingPolicy rounding, Clock clock) {
        this.receivables = receivables;
        this.currencies = currencies;
        this.baseRates = baseRates;
        this.fx = fx;
        this.engine = engine;
        this.registry = registry;
        this.rounding = rounding;
        this.clock = clock;
    }

    public RegistrationResult register(RegisterReceivableCommand cmd) {
        Money face = Money.of(cmd.faceValue(), Currency.BRL);
        if (face.amount().signum() <= 0) {
            throw new IllegalArgumentException("Valor de face deve ser positivo");
        }
        registry.require(cmd.type());
        Currency payment = currencies.find(cmd.paymentCurrency())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Moeda de pagamento nao suportada: " + cmd.paymentCurrency()));
        LocalDate today = LocalDate.now(clock);
        if (!cmd.dueDate().isAfter(today)) {
            throw new IllegalArgumentException(
                    "Vencimento deve ser futuro: " + cmd.dueDate());
        }
        // Prazo minimo JA NO CADASTRO: vencimento futuro porem < 1 mes criaria um recebivel
        // OPEN insimulavel/inliquidavel para sempre (TermCalculator exige >= 1) — a mesma
        // regra tem que valer nas duas portas (achado B4). Lanca InvalidTermException -> 422.
        TermCalculator.termMonths(today, cmd.dueDate());
        if (!receivables.cedenteExists(cmd.cedenteId())) {
            throw new CedenteNotFoundException(cmd.cedenteId());
        }

        return receivables.insertIdempotent(cmd.cedenteId(), cmd.type(), face.amount(),
                        payment.code(), cmd.dueDate(), cmd.creationKey())
                .map(row -> new RegistrationResult(row, true))
                .orElseGet(() -> {
                    // Conflito na creation_key: e replay. So devolve o original se o payload
                    // for o MESMO; payload divergente com a mesma chave e erro do cliente (422),
                    // coerente com a matriz de idempotencia da liquidacao.
                    ReceivableRow existente = receivables.findByCreationKey(cmd.creationKey())
                            .orElseThrow();
                    if (!mesmoPayload(existente, cmd, face)) {
                        throw new CreationKeyReuseException();
                    }
                    return new RegistrationResult(existente, false);
                });
    }

    private static boolean mesmoPayload(ReceivableRow row, RegisterReceivableCommand cmd, Money face) {
        return row.cedenteId() == cmd.cedenteId()
                && row.type().equals(cmd.type())
                && row.faceValue().compareTo(face.amount()) == 0
                && row.paymentCurrency().equals(cmd.paymentCurrency())
                && row.dueDate().equals(cmd.dueDate());
    }

    /** Precifica com as vigências de AGORA, sem persistir nada (indicativa — premissa B9). */
    public SimulationResult simulate(SimulationCommand cmd) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        Currency payment = currencies.find(cmd.paymentCurrency())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Moeda de pagamento nao suportada: " + cmd.paymentCurrency()));

        int termMonths = TermCalculator.termMonths(today, cmd.dueDate());
        BaseRate baseRate = baseRates.asOf(now)
                .orElseThrow(() -> new IllegalStateException("Sem taxa base vigente"));

        ExchangeRateRow fxRow = null;
        FxRate fxRate = null;
        if (!Currency.BRL.equals(payment)) {
            fxRow = fx.current(payment.code(), Currency.BRL.code(), now);
            fxRate = new FxRate(payment, Currency.BRL, fxRow.rate());
        }

        PricingContext ctx = new PricingContext(
                new MonthlyRate(baseRate.monthlyRate()), rounding);
        PricingResult result = engine.price(new PricingRequest(
                cmd.type(), Money.of(cmd.faceValue(), Currency.BRL), termMonths, payment, fxRate),
                ctx);

        return new SimulationResult(
                termMonths,
                today,
                result.presentValueBrl().amount().toPlainString(),
                result.discountBrl().amount().toPlainString(),
                result.paidAmount().amount().toPlainString(),
                result.paidAmount().currency().code(),
                baseRate.monthlyRate().toPlainString(),
                registry.require(cmd.type()).spread().value().toPlainString(),
                fxRow == null ? null : fxRow.rate().toPlainString(),
                fxRow == null ? null : fxRow.validFrom());
    }
}
