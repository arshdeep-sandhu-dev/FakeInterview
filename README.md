# Mock Agentic Debugging Interview

A practice setup for the "AI-assisted debugging" interview format. You get a codebase too big to
read end to end, a bug report written by a non-engineer, and an AI coding agent. You're graded
less on whether the bug gets fixed and more on **how you drive the agent and how you prove the
fix is right**.

```
FakeInterview/
|-- README.md             <- you are here (how to practise; no spoilers)
|-- pick-ticket.ps1 / .sh <- hands you a random bug ticket
|-- tickets/              <- 8 bug reports (SUP-101 ... SUP-108)
|-- shipfast/             <- the codebase. Open your AI agent HERE, and only here.
`-- interviewer/          <- SPOILERS: answer key, rubric, reference tests and fixes.
                             Don't open it until after a session.
```

> **Keep the agent out of `interviewer/`.** Start the agent with `shipfast/` as its working
> directory. If it asks to read files outside that folder, say no.

---

## One-time setup

```powershell
cd shipfast
.\test.ps1            # 87 tests, all green. The bugs slip past the existing tests, just like real life.
.\sim.ps1 --seed 42   # Black Friday load simulation + reconciliation (lots of [FAIL])

# Snapshot the buggy baseline so you can reset between sessions
git init; git add -A; git commit -m "baseline"
```

(macOS/Linux/Git Bash: `./test.sh`, `./sim.sh`.)

To reset after a session: `git reset --hard; git clean -fd`

---

## Running a session (45-60 min)

1. Get a random ticket. That's the only info you get up front. The picker lives in the
   `FakeInterview` folder, not in `shipfast`:

   ```powershell
   .\pick-ticket.ps1            # from FakeInterview\
   ..\pick-ticket.ps1           # from FakeInterview\shipfast\
   ..\pick-ticket.ps1 SUP-104   # a specific ticket
   ```

   `pick-ticket.sh` is only for Git Bash, macOS or Linux. PowerShell can't run `.sh` files.
2. Start a timer and **record your screen and voice**. The conversation is what's graded, so you
   need to hear yourself afterwards.
3. Talk out loud the whole time, as if an interviewer were watching.
4. Afterwards, open `interviewer/INTERVIEWER_GUIDE.md`, read that ticket's section, and score
   yourself with the rubric.

With a friend, they read the guide beforehand and play the interviewer. They can push on you with
the follow-up questions listed for each ticket.

**Harder variants:** SUP-103 + SUP-104 together (they interact). SUP-106 + SUP-108 together (both
break "Refund in full"). Or run the sim and fix whatever `[FAIL]` you pick, with no ticket at all.

---

## The loop to practise

The interview rewards a visible, disciplined loop. The agent can write code faster than you. Your
job is to steer it and not trust it blindly.

### 1. Understand before you prompt (2-5 min)
- Restate the symptom in your own words: *"So the observable problem is X, and the expected
  behaviour is Y."*
- Pull out the facts in the ticket: which numbers, times, and "only sometimes" patterns. Tickets
  bury clues, like "only around midnight", "only B2B accounts", or "can't reproduce by hand".
- Say what kind of bug you suspect **and why**: *"Can't reproduce by hand, but it happens under
  load. That smells like a race or a retry, not bad arithmetic."*

### 2. Reproduce
- Can you see it without reading code? Here, `sim` is your "production". Say which `[FAIL]`
  line matches the ticket.
- Use knobs to narrow it: `--threads 1` (does it go away without concurrency?), different seeds
  (is it deterministic?).

### 3. Investigate with the agent
Ask it to **map and explain first, then edit**. Good prompts:
- *"Don't change anything yet. Trace the code path for X from the controller down and list every
  place that reads or writes Y."*
- *"Here's the ticket. Give me your top 3 hypotheses ranked by likelihood, and what evidence in
  the code supports each one."*
- *"What are all the ways this method can exit? Normal return, each exception, early returns."*
- *"Is this pattern repeated anywhere else in the codebase?"*

When it answers, **check the claim** by opening the file and line yourself. Say out loud what you
agree and disagree with.

### 4. Write a failing test first, and watch it fail
- *"Write a unit test that reproduces this bug using the ticket's example. Don't fix anything
  yet."*
- **Run it and show it's red, and red for the right reason.** A test that fails because of a
  typo proves nothing.
- For flaky or concurrency bugs, loop it or use more threads until it fails reliably. Say how
  reliable it is.

### 5. Fix the root cause
- *"Fix the root cause, not the symptom. Explain why it's the root cause before you change
  anything."*
- Keep the diff small. Reject fixes that clamp, retry, catch-and-ignore, special-case the example,
  or change the test.

### 6. Prove it. "How do you know it's correct?"
You **will** be asked this. Have an answer with layers:
1. The reproduction test was red before the fix and is green after.
2. **Revert-check:** undo just the fix and confirm the test goes red again. This shows the test
   actually covers the fix.
3. The full test suite is still green (no regressions).
4. **Edge-case tests the AI didn't think of.** Boundaries (0, 1, limit, limit+1), nulls or missing
   optional fields, concurrency, time zones and DST, rounding and scale, every exit path
   (exceptions!), and repeated or duplicated requests.
5. System-level check: rerun `sim` across several seeds and show the relevant `[FAIL]` turned
   `[ OK ]` and nothing else turned red.
6. For concurrency: run the test many times. Say "passed 200/200" and explain why that's evidence
   but not proof.

### 7. Root cause or masked? Ask yourself out loud:
- Would this fix still work for inputs **other than the example** in the ticket?
- Did I fix the **mechanism**, or just stop this **one symptom** from showing?
- Does the **same pattern** exist elsewhere (other methods, other callers)?
- Did I change a **test, threshold, or config** to make it pass? (Red flag.)
- If I delete the workaround in the ticket, does everything still work?

### 8. If the fix doesn't work: have a plan B
Say the next steps before you need them: *"If this test still fails, my next hypothesis is X. I'd
check it by Y. If that's also wrong, I'd add logging or instrumentation at Z and rerun the sim."*
When a fix only **partly** works (the unit test is green but the sim is still red), that usually
means there's a **second cause**. Say so, and go find it.

### 9. Integrate the fix
- **Blast radius:** who else calls the code you changed? Do their assumptions still hold?
- **Existing bad data:** the fix stops new damage. What about records already wrong in production
  (double charges, stuck stock, wrong statuses)? Propose a backfill or repair.
- **Rollout:** feature flag? Deploy timing (not during the sale)? Is it safe to roll back?
- **Monitoring:** which metric or alert would have caught this, and will tell you the fix works in
  production?
- **Follow-ups:** what you'd file as separate tickets instead of cramming into this change.

---

## Questions interviewers ask (practise answering them)

- "How did you know it was correct?"
- "How do you know the AI's fix addresses the root cause and doesn't just hide the symptom?"
- "What edge cases did you test that the AI didn't suggest?"
- "Your test passes. Would it have failed before your fix? Show me."
- "What would you have done if that fix hadn't worked?"
- "Are there other places in the codebase with the same problem?"
- "What happens to customers already affected by this bug?"
- "How would you roll this out safely? What would you monitor?"
- "Where did you disagree with the AI, and why?"
- "If you had another hour, what would you do?"

## Useful phrases

- "Before I let it edit anything, I want it to explain the flow."
- "That's plausible, but let me verify that line myself."
- "I want to see this test fail first."
- "This fixes the example, but I'm not convinced it fixes the mechanism."
- "The unit test is green but the sim is still red. There's a second cause."
- "I'm going to revert just the fix to prove the test catches it."
- "This part is a product decision, not an engineering one. I'd confirm with finance or
  marketing."
