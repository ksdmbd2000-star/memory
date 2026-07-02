import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Match the old Gemini summarize button block and remove it
pattern = r'                Spacer\(modifier = Modifier\.height\(4\.dp\)\)\n                // Gemini AI Summarize Button \(span 2\).*?\}\n                \}\n'

content = re.sub(pattern, '', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
