# Monitoring, alerting and logging

| Component | Role |
|---|---|
| Spring Boot Actuator + Micrometer | App exposes metrics at `/actuator/prometheus` |
| Prometheus | Scrapes app + Kubernetes metrics every 15s, evaluates alert rules |
| kube-state-metrics / node-exporter / cAdvisor | Infrastructure metrics: replicas, restarts, CPU, memory |
| Grafana | Dashboard "FinSecure Pay - Transaction Validator" |
| Alertmanager | Routes FinSecure alerts to email (Gmail SMTP) |
| Azure Container Insights (Log Analytics) | Centralised, searchable container logs |

## Install

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
kubectl create namespace monitoring

# Gmail app password for Alertmanager, stored as a Kubernetes Secret (never in Git)
kubectl -n monitoring create secret generic alertmanager-smtp --from-literal=password='<16-char-app-password>'

helm install monitoring prometheus-community/kube-prometheus-stack -n monitoring \
  -f monitoring/values.yaml --set grafana.adminPassword='<choose-a-password>'

kubectl apply -f monitoring/servicemonitor.yaml -f monitoring/alert-rules.yaml -f monitoring/grafana-dashboard.yaml
```

## Access (port-forward only; nothing exposed publicly)

```bash
kubectl -n monitoring port-forward svc/monitoring-grafana 3000:80                          # http://localhost:3000 (admin)
kubectl -n monitoring port-forward svc/monitoring-kube-prometheus-prometheus 9090:9090     # http://localhost:9090
kubectl -n monitoring port-forward svc/monitoring-kube-prometheus-alertmanager 9093:9093   # http://localhost:9093
```

## Alerts

| Alert | Condition | Severity |
|---|---|---|
| FinSecureHighRejectionRate | > 0.5 rejected transactions/sec on `/validate` for 1 min | warning |
| FinSecureReplicasUnavailable | < 2 available replicas for 2 min | critical |

Trigger the rejection alert: `./scripts/traffic.sh <EXTERNAL-IP> attack`

## Logs and troubleshooting

Application logs go to stdout; Kubernetes captures them per container. Account numbers are masked (`****5678`) before logging.

**Quick, live (kubectl):**

```bash
kubectl get pods -l app=finsecure                       # which pods exist, are they Ready / restarting?
kubectl logs -l app=finsecure --tail=100                # recent logs from all app pods
kubectl logs -f <pod-name>                              # follow one pod live
kubectl logs <pod-name> --previous                      # logs from the crashed previous container
kubectl logs -l app=finsecure --tail=500 | grep REJECTED   # rejection reasons
kubectl describe pod <pod-name>                         # Events: probe failures, image pulls, OOMKilled
kubectl get events --sort-by=.lastTimestamp | tail -20
```

**Centralised, history and search (Azure Container Insights):** Azure portal -> finsecure-aks -> Monitoring -> Logs.

```kusto
ContainerLogV2
| where PodName startswith "finsecure"
| where LogMessage has "REJECTED"
| project TimeGenerated, PodName, LogMessage
| order by TimeGenerated desc
```

Logs survive pod restarts and deletions here, unlike `kubectl logs`.

**Typical troubleshooting flow:** alert email -> Grafana (which metric, since when) -> `kubectl get pods` / `describe` (pod health, events) -> logs (kubectl for live, Log Analytics for history) -> fix -> pipeline redeploys or `kubectl rollout undo`.
