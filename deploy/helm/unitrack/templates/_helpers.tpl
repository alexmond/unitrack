{{/*
Expand the name of the chart.
*/}}
{{- define "unitrack.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name.
*/}}
{{- define "unitrack.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "unitrack.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels.
*/}}
{{- define "unitrack.labels" -}}
helm.sh/chart: {{ include "unitrack.chart" . }}
{{ include "unitrack.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "unitrack.selectorLabels" -}}
app.kubernetes.io/name: {{ include "unitrack.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Name of the bundled PostgreSQL StatefulSet/Service.
*/}}
{{- define "unitrack.postgresql.fullname" -}}
{{- printf "%s-postgresql" (include "unitrack.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "unitrack.postgresql.selectorLabels" -}}
app.kubernetes.io/name: {{ include "unitrack.name" . }}-postgresql
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: database
{{- end }}

{{/*
ServiceAccount name.
*/}}
{{- define "unitrack.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "unitrack.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Image reference (tag falls back to the chart appVersion).
*/}}
{{- define "unitrack.image" -}}
{{- printf "%s:%s" .Values.image.repository (default .Chart.AppVersion .Values.image.tag) }}
{{- end }}

{{/*
JDBC URL: bundled Postgres service when enabled, else the external URL.
*/}}
{{- define "unitrack.jdbcUrl" -}}
{{- if .Values.postgresql.enabled -}}
jdbc:postgresql://{{ include "unitrack.postgresql.fullname" . }}:5432/{{ .Values.postgresql.auth.database }}
{{- else -}}
{{- required "externalDatabase.url is required when postgresql.enabled=false" .Values.externalDatabase.url -}}
{{- end -}}
{{- end }}

{{/*
DB username.
*/}}
{{- define "unitrack.dbUser" -}}
{{- if .Values.postgresql.enabled -}}
{{- .Values.postgresql.auth.username -}}
{{- else -}}
{{- .Values.externalDatabase.user -}}
{{- end -}}
{{- end }}

{{/*
Name of the Secret holding the DB password (and optional tokens).
Prefers an existing Secret when the user supplies one.
*/}}
{{- define "unitrack.secretName" -}}
{{- if and (not .Values.postgresql.enabled) .Values.externalDatabase.existingSecret -}}
{{- .Values.externalDatabase.existingSecret -}}
{{- else if .Values.secret.existingSecret -}}
{{- .Values.secret.existingSecret -}}
{{- else -}}
{{- include "unitrack.fullname" . -}}
{{- end -}}
{{- end }}

{{/*
Key within the Secret that holds the DB password.
*/}}
{{- define "unitrack.dbPasswordKey" -}}
{{- if and (not .Values.postgresql.enabled) .Values.externalDatabase.existingSecret -}}
{{- .Values.externalDatabase.existingSecretPasswordKey -}}
{{- else -}}
{{- .Values.secret.keys.dbPassword -}}
{{- end -}}
{{- end }}

{{/*
Whether the chart manages its own Secret (vs. the user bringing an existing one).
*/}}
{{- define "unitrack.createSecret" -}}
{{- if and (not .Values.postgresql.enabled) .Values.externalDatabase.existingSecret -}}
false
{{- else if .Values.secret.existingSecret -}}
false
{{- else -}}
true
{{- end -}}
{{- end }}
