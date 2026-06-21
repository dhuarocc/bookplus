# Jenkins a nivel experto: Shared Library, JCasC, blue-green, Vault, notificaciones

Sobre el pipeline avanzado (ver `JENKINS.md`), se añaden las prácticas que distinguen a un
equipo **senior/experto** con Jenkins: pipeline reutilizable, configuración reproducible,
despliegue seguro y secretos centralizados.

## 1. Shared Library — el pipeline reutilizable

En vez de copiar un `Jenkinsfile` de 150 líneas en cada repositorio, toda la lógica vive en
una **Shared Library** versionada (`jenkins-shared-library/`):

- `vars/bookplusPipeline.groovy` — define el pipeline declarativo completo como una función
  `call(Map cfg)`.
- `vars/notify.groovy` — notificaciones (Slack/email), tolerante a fallos de configuración.
- `vars/blueGreenDeploy.groovy` — el despliegue blue-green con rollback.
- `src/com/bookplus/ci/Services.groovy` — la lista central de microservicios.

Resultado: el **`Jenkinsfile` del repo queda en ~10 líneas**:

```groovy
@Library('bookplus-shared-lib@main') _
bookplusPipeline(registry: 'ghcr.io/dhuarocc')
```

Cualquier mejora del pipeline se hace **una vez** en la librería y la heredan todos los repos.
(En un setup real, la librería va en su **propio repositorio** —p. ej.
`bookplus-jenkins-shared-lib`— y se referencia por Git; aquí está versionada en la carpeta
`jenkins-shared-library/` para tenerla a la vista. También se deja `Jenkinsfile.standalone`
como versión self-contained por si se prefiere no usar la librería.)

## 2. JCasC (Configuration as Code) — Jenkins reproducible

`jenkins/casc/jenkins.yaml` define **toda** la configuración de Jenkins como código: seguridad,
credenciales, la Shared Library, el servidor de SonarCloud, Vault, Slack y el job multibranch.
Con `JAVA_OPTS=-Djenkins.install.runSetupWizard=false` y `CASC_JENKINS_CONFIG`, Jenkins arranca
**sin asistente y ya configurado** — nada se clica a mano, así que un Jenkins nuevo es idéntico
al anterior (reproducibilidad, disaster recovery, revisión por PR).

Los **secretos no se hardcodean**: el YAML usa `${GHCR_TOKEN}`, `${SONAR_TOKEN}`, etc., que se
inyectan por variables de entorno (definidas en un `.env` junto al compose).

## 3. Multibranch Pipeline + escaneo de PRs

El job se crea por código (job-dsl dentro de JCasC) como **Multibranch Pipeline**: Jenkins
escanea el repositorio y crea automáticamente un pipeline por **cada rama y Pull Request**,
descubriéndolos por el `Jenkinsfile`. Los items huérfanos (ramas borradas) se limpian solos.

## 4. Despliegue blue-green con rollback automático

`blueGreenDeploy.groovy` implementa la estrategia: dos stacks idénticos (**blue**/**green**),
solo uno recibe tráfico. Se despliega la versión nueva en el color **inactivo**, se valida con
un **health check**, y solo entonces se **conmuta el tráfico**. Si el health check falla, se
**revierte automáticamente** al color anterior — cero downtime y rollback seguro. Incluye
aprobación manual con timeout antes de tocar producción.

(Los comandos de infraestructura van como `echo` demostrativos; en real se sustituyen por los
`docker compose`/`kubectl`/cambio de balanceador correspondientes.)

## 5. Notificaciones (Slack / email)

El bloque `post` del pipeline llama a `notify(status)`, que envía el resultado a **Slack** y, en
caso de fallo, un **email**. Es tolerante: si el plugin/credencial no está configurado, lo
registra en el log sin romper el build. El servidor de Slack se configura por JCasC.

## 6. Credenciales desde HashiCorp Vault

Encaja con el **Vault** que ya usa el backend. El plugin `hashicorp-vault-plugin` se configura
por JCasC (URL de Vault + credencial **AppRole**), y el pipeline puede leer secretos en tiempo
de ejecución:

```groovy
withVault(vaultSecrets: [[path: 'secret/bookplus/ci',
        secretValues: [[envVar: 'DEPLOY_KEY', vaultKey: 'deploy_key']]]]) {
    sh 'deploy --key $DEPLOY_KEY'
}
```

Así los secretos del pipeline **no viven en Jenkins**, sino en Vault (rotables, auditables),
con un único punto de verdad para toda la plataforma.

## Plugins (en `jenkins/Dockerfile`)

`configuration-as-code`, `job-dsl`, `hashicorp-vault-plugin`, `slack`, `email-ext`,
`pipeline-utility-steps`, además de los del pipeline base (Pipeline, Docker Pipeline, JUnit,
AnsiColor, Workspace Cleanup, SonarQube Scanner).

## Verificación

Todo esto es **infraestructura/configuración** (no hay test unitario). Se verifica levantando
Jenkins con el compose (`up -d --build`): debe arrancar **sin asistente**, con el job `bookplus`
y la Shared Library ya configurados. La Shared Library, para usarse vía `@Library`, debe estar
publicada en su repositorio Git (o configurada como ruta local).
