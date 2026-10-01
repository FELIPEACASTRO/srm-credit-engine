# Config ASSADA na imagem (mesmo padrao de infra/db): sem bind-mount, roda em
# qualquer Windows sem depender do File Sharing do Docker Desktop.
FROM prom/prometheus:v3.1.0
COPY prometheus.yml /etc/prometheus/prometheus.yml
