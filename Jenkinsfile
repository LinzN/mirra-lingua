// Builds the MirraLingua server JAR and the Android APK.
// Optional: set LINGUA_CONTENT_TARGET (e.g. "lingua@mirranet.de:/opt/mirra-lingua/content/") to publish
// course files after a successful build – the app picks them up on its next sync.
pipeline {
    agent any
    options { buildDiscarder(logRotator(numToKeepStr: '20')) }

    stages {
        stage('Server') {
            steps {
                // catchError: a failing server build should not stop the Android build (and vice versa).
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
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
                        '''
                        // Maven wrapper: downloads Maven on first use, no Maven install needed on the agent.
                        sh 'chmod +x mvnw && ./mvnw -B clean verify'
                    }
                }
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'server/target/surefire-reports/*.xml' }
                success { archiveArtifacts artifacts: 'server/target/mirra-lingua-server.jar', fingerprint: true }
            }
        }
        stage('Android') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    dir('android') {
                        // Sets up the Android SDK on first run (~/android-sdk unless ANDROID_HOME is set), then builds.
                        sh '''
                            chmod +x gradlew scripts/setup-android-sdk.sh
                            # Provides a JDK Gradle can run on (17-24) and the Android SDK; aborts the build if that fails.
                            scripts/setup-android-sdk.sh > .build-env
                            . ./.build-env
                            ./gradlew --no-daemon testDebugUnitTest assembleRelease
                        '''
                    }
                }
            }
            post {
                always { junit allowEmptyResults: true, testResults: 'android/app/build/test-results/**/*.xml' }
                success { archiveArtifacts artifacts: 'android/app/build/outputs/apk/release/*.apk', fingerprint: true }
            }
        }
        stage('Publish content') {
            when { allOf { branch 'main'; expression { env.LINGUA_CONTENT_TARGET && currentBuild.currentResult == 'SUCCESS' } } }
            steps {
                sh 'rsync -av --delay-updates content/courses/*.json "$LINGUA_CONTENT_TARGET"'
            }
        }
    }
}
