// El pipeline completo vive en la Shared Library 'bookplus-shared-lib' (carpeta
// jenkins-shared-library/), configurada globalmente vía JCasC (jenkins/casc/jenkins.yaml).
// Así el Jenkinsfile de CADA repo queda en unas pocas líneas.
//
// (Si no quieres usar la Shared Library, existe la versión self-contained en
//  Jenkinsfile.standalone.)
@Library('bookplus-shared-lib@main') _

bookplusPipeline(
    registry:   'ghcr.io/dhuarocc',
    mavenImage: 'maven:3.9-eclipse-temurin-21'
)
