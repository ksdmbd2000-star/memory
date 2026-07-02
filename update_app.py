import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Update invocation
old_invocation = """                BentoVoiceRecorderApp(
                    signedInEmail = signedInAccountEmail,
                    onSignInClick = { triggerGoogleSignIn() },
                    onSignOutClick = { triggerSignOut() },
                    onSavePastAudioClick = { minutes -> triggerMergeAndUpload(minutes) },
                    onRequestShowAuthHelp = { showAuthHelpDialog = true },
                    onStartRecordingClick = { triggerStartRecording() },
                    onStopRecordingClick = { triggerStopRecording() },
                    onSummarizeClick = { minutes -> triggerMergeAndSummarize(minutes) }
                )"""
new_invocation = """                BentoVoiceRecorderApp(
                    signedInEmail = signedInAccountEmail,
                    onSignInClick = { triggerGoogleSignIn() },
                    onSignOutClick = { triggerSignOut() },
                    onSaveAndSummarizeClick = { minutes -> triggerMergeUploadAndSummarize(minutes) },
                    onRequestShowAuthHelp = { showAuthHelpDialog = true },
                    onStartRecordingClick = { triggerStartRecording() },
                    onStopRecordingClick = { triggerStopRecording() }
                )"""
content = content.replace(old_invocation, new_invocation)

# Update definition
old_def = """fun BentoVoiceRecorderApp(
    signedInEmail: String?,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSavePastAudioClick: (Int) -> Unit,
    onRequestShowAuthHelp: () -> Unit = {},
    onStartRecordingClick: () -> Unit = {},
    onStopRecordingClick: () -> Unit = {},
    onSummarizeClick: (Int) -> Unit = {}
)"""
new_def = """fun BentoVoiceRecorderApp(
    signedInEmail: String?,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSaveAndSummarizeClick: (Int) -> Unit,
    onRequestShowAuthHelp: () -> Unit = {},
    onStartRecordingClick: () -> Unit = {},
    onStopRecordingClick: () -> Unit = {}
)"""
content = content.replace(old_def, new_def)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
