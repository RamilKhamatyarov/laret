# Demo: dry run — the global --dry-run flag narrates side-effects to stderr
# without touching disk (mirrors README "Dry Run"). The failing `ls` proves
# nothing was written. Rendered in CI by .github/workflows/docs.yml.

Output demo/dry-run.gif

Set Shell "bash"
Set FontSize 18
Set Width 1200
Set Height 600
Set Theme "Dracula"
Set TypingSpeed 50ms
Set Padding 20

Hidden
Type "mkdir -p /tmp/demo-dry && cd /tmp/demo-dry && rm -f notes.txt && echo 'quarterly numbers' > report.txt && clear"
Enter
Sleep 1s
Show

Type `laret file create notes.txt --content "hello" --dry-run`
Sleep 500ms
Enter
Sleep 3500ms

Type "ls notes.txt"
Sleep 500ms
Enter
Sleep 2s

Type "laret file delete report.txt --dry-run"
Sleep 500ms
Enter
Sleep 3500ms
