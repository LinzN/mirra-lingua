// Builds the Parla server (Rust binary + Debian package) and the Android APK.
// Optional: set PARLA_CONTENT_TARGET (e.g. "root@mirranet.de:/var/lib/parla/content/") to publish
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
                        // Installs Rust via rustup on first run if cargo is missing (needs a C compiler for SQLite).
                        sh '''
                            chmod +x scripts/setup-rust.sh
                            scripts/setup-rust.sh > .build-env
                            . ./.build-env
                            cargo test --locked
                            cargo build --release --locked
                            # Debian package: target/debian/parla-server_<version>_<arch>.deb
                            SKIP_BUILD=1 packaging/build-deb.sh
                        '''
                    }
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'server/target/debian/*.deb, server/target/release/parla-server', fingerprint: true
                }
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
            when { allOf { branch 'main'; expression { env.PARLA_CONTENT_TARGET && currentBuild.currentResult == 'SUCCESS' } } }
            steps {
                sh 'rsync -av --delay-updates content/courses/*.json "$PARLA_CONTENT_TARGET"'
            }
        }
    }
}
