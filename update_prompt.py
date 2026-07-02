import re

with open('app/src/main/java/com/example/GeminiSummarizer.kt', 'r') as f:
    content = f.read()

old_header = """val header = "{\\"contents\\":[{\\"parts\\":[{\\"text\\":\\"この音声は、家族の日常会話の録音データです。私は夫（ひろき、ひろ）で、会話の理解や記憶に自信がないため、妻（ちえみ、ちぃ、ちー）から言われたことや、子ども（ちひろ、ちーたん、ちっち）に関する内容を理解し、記憶するための備忘録として利用します。妻が私に伝えたかったこと、頼んだこと、私がやるべきこと、今日や今後の予定などを中心に、分かりやすく整理して箇条書きで教えてください。\\"},{\\"inlineData\\":{\\"mimeType\\":\\"audio/wav\\",\\"data\\":\\\"\""""

new_prompt = """この音声は、家族の日常会話の録音データです。私は夫（ひろき、ひろ）で、会話の理解や記憶に自信がないため、妻（ちえみ、ちぃ、ちー）から言われたことや、子ども（ちひろ、ちーたん、ちっち）に関する内容を理解し、記憶するための備忘録として利用します。

以下の2つの情報を出力してください。後から検証できるように、必ず『===要約===』と『===文字起こし===』という区切り文字を含めてください。

===要約===
妻が私に伝えたかったこと、頼んだこと、私がやるべきこと、今日や今後の予定などを中心に、分かりやすく整理した箇条書きの要約。

===文字起こし===
音声の文字起こし。発言者（夫、妻、子どもなど）とタイムスタンプ（[00:00]のような形式）を含めてください。"""

# Escape newlines and quotes for JSON string
new_prompt_escaped = new_prompt.replace('\n', '\\n').replace('"', '\\"')

new_header = f"""val header = "{{\\"contents\\":[{{\\"parts\\":[{{\\"text\\":\\"{new_prompt_escaped}\\"}},{{\\"inlineData\\":{{\\"mimeType\\":\\"audio/wav\\",\\"data\\":\\\"\""""

content = content.replace(old_header, new_header)

with open('app/src/main/java/com/example/GeminiSummarizer.kt', 'w') as f:
    f.write(content)
