// Builds the MirraLingua server JAR and the Android APK.
// Optional: set LINGUA_CONTENT_TARGET (e.g. "lingua@mirranet.de:/opt/mirra-lingua/content/") to publish
// course files after a successful build – the app picks them up on its next sync.
pipeline {
    agent any
    options { buildDiscarder(logRotator(numToKeepStr: '20')) }

    stages {
        stage('Server') {
            steps {
                dir('server') {
                    sh 'mvn -B clean verify'
                }
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'server/target/surefire-reports/*.xml' }
                success { archiveArtifacts artifacts: 'server/target/mirra-lingua-server.jar', fingerprint: true }
            }
        }
        stage('Android') {
            when { expression { env.ANDROID_HOME || env.ANDROID_SDK_ROOT } }
            steps {
                dir('android') {
                    sh 'chmod +x gradlew && ./gradlew --no-daemon testDebugUnitTest assembleRelease'
                }
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'android/app/build/test-results/**/*.xml' }
                success { archiveArtifacts artifacts: 'android/app/build/outputs/apk/release/*.apk', fingerprint: true }
            }
        }
        stage('Publish content') {
            when { allOf { branch 'main'; expression { env.LINGUA_CONTENT_TARGET } } }
            steps {
                sh 'rsync -av --delay-updates content/courses/*.json "$LINGUA_CONTENT_TARGET"'
            }
        }
    }
}
