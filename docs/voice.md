# Voice guide for HomeRecord copy

Applies to every user-facing string in both apps (`strings.xml`), the store listings, the privacy policy, the Data safety notes, release notes and anything else a person reads. Written from Nate's own writing style. The apps should read like a plain-spoken homeowner or electrician wrote them, not like a template.

## Hard rules

1. No dashes in prose. No em dashes, en dashes or spaced hyphens joining clauses. Use a comma, a period or parentheses. The "2 · 4" slot separator and "2-pole" are labels, not prose, and stay.
2. No filler or marketing words. Never: seamless, robust, leverage, journey, effortless, empower, elevate, unlock (except literally unlocking Pro), supercharge, "peace of mind", "at your fingertips", "all in one place", "take control", "you're all set", "oops", "uh-oh", "something went wrong" with no detail, "Great!", "Awesome!", "Let's get started!".
3. No "not X, but Y" constructions, no rhetorical questions, no stacked adjectives ("fast, simple, powerful"), no semicolons, no emoji in app copy, no exclamation points except a single one on a real pleasantry ("Thanks!").
4. Specifics over polish. Say what happened and what to do next: "Couldn't save the photo. Check free space and try again." beats "Something went wrong."
5. Short over clever. One idea per sentence. Titles are nouns ("Breaker", "Shutoffs"), buttons are verbs ("Save", "Add breaker", "Retry").
6. Plain words: "sort out", "walk through", "heads up", "either way", "when you get the chance". Never "please note that", "kindly", "utilize", "ensure" (say "make sure"), "in order to" (say "to").
7. Keep the user's own terms. Breaker, panel, slot, shutoff, main, two-pole, tandem, GFCI, AFCI. Electricians and homeowners both know them. Explain a term once, in a helper line, only where a first-time homeowner would be lost (for example what a two-pole breaker is).
8. Capitalization: sentence case everywhere ("Add breaker", not "Add Breaker"). App names and proper nouns keep their capitals (House Manual, Field Log, Google Play, RevenueCat is never shown to users).
9. Consistent terms across both apps. One word per thing: "shutoff" (not "shut-off" or "valve"), "photo" (not "image" or "picture"), "job" in Field Log, "home" in House Manual, "handoff" for the Field Log to House Manual transfer, "backup" for the encrypted file, "legend" for the panel PDF, "Pro" for the paid tier.

## Patterns

| Where | Shape | Example |
| --- | --- | --- |
| Empty state | What this screen holds, one line, then the action | "No breakers yet. Tap a slot to add one." |
| Error | What failed, why if known, what to do | "Can't load prices right now. Check your connection and tap Retry." |
| Confirmation | What will happen, in plain words, destructive verb on the button | "Delete this room? Its breaker links stay on the panel." / button "Delete" |
| Permission | Why the app needs it, what happens without it | "Field Log uses the camera to take job photos. Without it you can still add notes and voice memos." |
| Success | Usually none. Show the result instead of a toast. If a toast is needed, state the fact | "Legend saved to Downloads" |
| Helper text | One short sentence under the field, no "please" | "Amps as printed on the breaker handle." |
| Onboarding | What to do first and why it pays off, two sentences max | "Start with the panel. Once the breakers are labeled, every room lookup and the emergency card work from it." |
| Paywall | What Pro adds, the two plans, restore link. No urgency, no countdown, no "best value" badges | "Pro removes the one-panel and ten-equipment limits and adds reminders. Annual or lifetime, your call." |
| Store listing | First line says what the app does for a homeowner or a tradesperson, then three or four concrete things it does, then what it does not do (no account, no cloud) | "House Manual maps your breaker panel, shutoffs and equipment so anyone in the house can find them in a hurry." |

## Review checklist for a language pass

- Read every string aloud. If it sounds like a product page or a chatbot, rewrite it plainer.
- Search for: " - ", "—", "–", "seamless", "robust", "leverage", "journey", "effortless", "oops", "uh", "!", "please", "ensure", "utilize", "in order to", "successfully" (drop it: "Saved" not "Successfully saved"), "Error:" (say what happened instead).
- Check every string that ends in "..." is an action in progress ("Saving…") and uses the single ellipsis character.
- Check both apps use the same word for the same thing (list in rule 9).
- Keep placeholders and plurals intact; keep string names unchanged so tests and code still compile. If a string's meaning changes enough that a test asserts on it, update the test in the same commit.
- Check the `docs/store-listing.md`, `docs/privacy-policy.md` and `docs/data-safety.md` with the same rules. The privacy policy stays accurate above all; plain language is not a reason to drop a required disclosure.
