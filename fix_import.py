with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace("            import kotlinx.coroutines.async", "")
if "import kotlinx.coroutines.async" not in content:
    content = content.replace("import kotlinx.coroutines.launch", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.async")

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
