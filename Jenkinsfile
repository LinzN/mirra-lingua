// Builds the Parla Android app (APK). The server lives in LinzN/parla-server.
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
    }
}
