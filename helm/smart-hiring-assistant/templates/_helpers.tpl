{{/* Chart name, truncated to the Kubernetes label limit. */}}
{{- define "smart-hiring-assistant.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Selector labels shared by every workload in this release. */}}
{{- define "smart-hiring-assistant.selectorLabels" -}}
app.kubernetes.io/name: {{ include "smart-hiring-assistant.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/* Common labels. */}}
{{- define "smart-hiring-assistant.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{ include "smart-hiring-assistant.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/* Service account used by the service pods. */}}
{{- define "smart-hiring-assistant.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "smart-hiring-assistant.name" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/* Image reference for a service: dict "root" $ "config" $config. */}}
{{- define "smart-hiring-assistant.serviceImage" -}}
{{- $image := .root.Values.image }}
{{- printf "%s/%s/smart-hiring/%s:%s" $image.registry $image.organization .config.image (.config.tag | default $image.tag | toString) }}
{{- end }}

{{/* Spring profile; "docker" resolves peers by service DNS name, which matches the Service names here. */}}
{{- define "smart-hiring-assistant.springProfile" -}}
{{- .Values.global.springProfile | default "docker" }}
{{- end }}
