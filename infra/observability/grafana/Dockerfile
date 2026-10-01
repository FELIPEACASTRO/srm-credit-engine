# Datasource + dashboard ASSADOS na imagem (mesmo padrao de infra/db): sem bind-mount,
# roda em qualquer Windows sem depender do File Sharing do Docker Desktop.
FROM grafana/grafana:11.4.0
COPY provisioning/ /etc/grafana/provisioning/
COPY dashboards/   /etc/grafana/dashboards/
