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
                    // The server needs JDK 21+. Fail early with a readable message instead of a compiler error.
                    sh '''
                        if ! command -v java >/dev/null 2>&1; then
                          echo "No Java found on this agent. Install a JDK 21 (e.g. apt install openjdk-21-jdk-headless) or set JAVA_HOME." >&2
                          exit 1
                        fi
                        major=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p' | cut -d. -f1)
                        if [ "${major:-0}" -lt 21 ]; then
                          echo "Java $major found, but JDK 21 or newer is required. Set JAVA_HOME to a JDK 21 for this job." >&2
                          exit 1
                        fi
                        if ! command -v javac >/dev/null 2>&1 && [ -z "$JAVA_HOME" ]; then
                          echo "Only a JRE was found; Maven needs a full JDK 21 (javac)." >&2
                          exit 1
                        fi
                    '''
                    // Maven wrapper: downloads Maven on first use, no Maven install needed on the agent.
                    sh 'chmod +x mvnw && ./mvnw -B clean verify'
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
