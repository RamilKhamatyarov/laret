# Demo: command piping — `laret pipe run` chains stages with `---`,
# feeding each stage's stdout into the next (mirrors README "Command Piping").
# Rendered in CI by .github/workflows/docs.yml; the GIF is never committed.

Output demo/piping.gif

Set Shell "bash"
Set FontSize 18
Set Width 1200
Set Height 600
Set Theme "Dracula"
Set TypingSpeed 50ms
Set Padding 20

Hidden
Type "mkdir -p /tmp/demo && cd /tmp/demo && touch alpha.txt beta.txt gamma.txt && clear"
Enter
Sleep 1s
Show

Type "laret pipe run dir list /tmp/demo --- file create /tmp/demo/listing.txt"
Sleep 500ms
Enter
Sleep 4s

Type "laret file read /tmp/demo/listing.txt"
Sleep 500ms
Enter
Sleep 4s
