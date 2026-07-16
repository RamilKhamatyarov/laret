# First-glance demo for README and the generated Pages landing page.
# Rendered by .github/workflows/docs.yml and copied to site/docs/assets.

Output demo/overview.gif

Set Shell "bash"
Set FontSize 18
Set Width 1200
Set Height 620
Set Theme "Dracula"
Set TypingSpeed 45ms
Set Padding 20

Hide
Type "mkdir -p /tmp/laret-overview && cd /tmp/laret-overview && rm -f greeting.txt && clear"
Enter
Sleep 1s
Show

Type `laret file create greeting.txt --content "Hello from Laret"`
Sleep 400ms
Enter
Sleep 2500ms

Type "laret file read greeting.txt"
Sleep 400ms
Enter
Sleep 2500ms

Type "laret file delete greeting.txt --dry-run"
Sleep 400ms
Enter
Sleep 2500ms

Type "test -f greeting.txt && echo 'greeting.txt still exists'"
Sleep 400ms
Enter
Sleep 2s