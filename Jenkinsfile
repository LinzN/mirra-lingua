// Builds the Parla client apps: Android (APK) and desktop (Linux .deb, Windows .msi).
// The server lives in Parla-Studio/parla-server.
//
// The .msi can only be built on Windows: set the environment variable PARLA_WINDOWS_AGENT
// (e.g. in the job or globally) to the label of a Windows agent with a JDK 17-21 to enable it.
pipeline {
    agent any
    options { buildDiscarder(logRotator(numToKeepStr: '20')) }

    stages {
        stage('Android') {
            steps {
                dir('android') {
                    // Provides a JDK Gradle can run on (17-24) and the Android SDK (installed on first run).
                    sh '''
                        chmod +x gradlew scripts/setup-android-sdk.sh
                        scripts/setup-android-sdk.sh > .build-env
                        . ./.build-env
                        ./gradlew --no-daemon testDebugUnitTest assembleRelease
                    '''
                }
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'android/app/build/test-results/**/*.xml' }
                success { archiveArtifacts artifacts: 'android/app/build/outputs/apk/release/*.apk', fingerprint: true }
            }
        }

        stage('Desktop Linux') {
            steps {
                dir('desktop') {
                    // JDK 17-21 (downloaded on first run if needed); jpackage needs dpkg-deb + fakeroot.
                    sh '''
                        chmod +x gradlew scripts/setup-jdk.sh
                        scripts/setup-jdk.sh > .build-env
                        . ./.build-env
                        ./gradlew --no-daemon distDeb
                    '''
                }
            }
            post {
                success { archiveArtifacts artifacts: 'desktop/build/dist/*.deb', fingerprint: true }
            }
        }

        stage('Desktop Windows') {
            when { beforeAgent true; expression { env.PARLA_WINDOWS_AGENT?.trim() } }
            agent { label "${env.PARLA_WINDOWS_AGENT}" }
            steps {
                dir('desktop') {
                    bat 'gradlew.bat --no-daemon distMsi'
                }
            }
            post {
                success { archiveArtifacts artifacts: 'desktop/build/dist/*.msi', fingerprint: true }
            }
        }
    }
}
