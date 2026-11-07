# HomeRecord privacy policy

Covers House Manual, Field Log and the HomeRecord server they sync with. Last updated 2026-11-07.

Operator and contact: see BLOCKERS.md "Privacy policy contact" (Nate fills in the company name and the address people write to before the policy is published).

## The short version

- Both apps keep your records on your phone first. They work with no account and no connection.
- Your data goes to our server only when you sign in, and then only to sync it between your phones, your crew or your family, and to make the share links and handoffs you ask for.
- We don't sell data, show ads, or use analytics or crash reporting SDKs.
- You can delete the cloud copy of a home from the app at any time. Your phone keeps its own copy.

## What each app keeps on your phone

**House Manual:** the home's floors, rooms and plan, wall photos and the pipes and wires drawn on them, items (make, model, serial, dates, warranty, photos, receipts), the breaker panel, shutoffs, documents, contacts, reminders and service history. Encrypted backups you make are files you choose where to put. The passphrase never leaves your phone and we can't recover it.

**Field Log:** your company, crew and jobs, punches with the time and (if you allow it) the location at clock in and clock out, photos, notes, voice memos, daily logs, tasks, plans and files, change orders with signatures, schedules, timesheets, materials and receipts, and clients' names, phones, emails and job addresses.

Everything sits in the app's private storage. Uninstalling the app removes it.

## What goes to the server, and why

| Data | When | Why | How long |
| --- | --- | --- | --- |
| Email address | You sign in with an email code or Google | Your account, sign-in codes, invites | Until the account is deleted |
| Sign-in sessions and limits | Each sign-in | Keep you signed in for 30 days, stop code guessing (counts per email and IP address, kept one hour) | 30 days for a session |
| The home's or company's records listed above | While signed in (House Manual: with Pro) | Sync between phones and people you invited | Until you delete them or the cloud copy |
| Photos, documents, voice memos, plans | While signed in | Same, stored as files in Cloudflare R2 | Until deleted. Files no record points to are removed after 30 days |
| Location of a punch | Field Log, when you clock in or out | The owner sees where a punch happened and "far from job" flags | As long as the punch |
| Push token | Field Log, signed in | Task, schedule and approval alerts | Until you sign out. The alert log is kept 30 days |
| Purchase status | You buy Pro or a Field Log plan | Unlock what you paid for | As long as the account |
| Sync bookkeeping | Each sync | Avoid double writes and record merge conflicts | 90 days |
| Handoff files | A contractor hands a house to a homeowner | Let the homeowner download it | 30 days |
| Share links | You make a link for a client or an invite | Show the photos or log you picked to whoever has the link, or let an invited person join | Until you revoke them. Invites expire after 14 days |

## Lookups that leave the server

Some features ask an outside service a question. Only what the question needs is sent, and never your email.

- **Warranty lookup and recalls (House Manual):** the item's make and model go to Anthropic's API, which searches manufacturer sites, and to the U.S. Consumer Product Safety Commission's recall service. Answers are cached by make and model for up to 180 days, with nothing tying them to you.
- **Reading a data plate (House Manual):** the photo of the label goes to Anthropic's API to read the make, model and serial. It is not stored by us.
- **Weather on a daily log (Field Log):** the job's latitude and longitude and the date go to Open-Meteo.
- **Sign-in codes and invite emails:** sent through Resend, which sees the address and the message.
- **Dictation (Field Log change orders):** uses your phone's speech service (usually Google). That audio goes to the speech provider, not to us.

## Companies that process data for us

- Cloudflare (server, database and file storage)
- Resend (email delivery)
- Anthropic (warranty lookup and data plate reading)
- Google (Google sign-in, Firebase Cloud Messaging for Field Log alerts, Play billing)
- RevenueCat (purchase status: it sees an app user ID and your purchases, never your records)

## Who can see your data

- **House Manual:** you, and anyone you invite to the home as family (can edit) or viewer (can look). A contractor who handed you the house does not keep access unless you invite them.
- **Field Log:** your company's owner and office see the company's records. Leads and crew see what their role allows (crew see their own punches and the jobs they are on). Anyone with a share link sees only what that link shows, until it is revoked.

## Your choices

- Use either app with no account. Nothing leaves the phone except the lookups above, which need a connection and which you start.
- Deny location in Field Log. You can still clock in unless your company requires location.
- Delete the cloud copy of a home in House Manual (More, Account and sync). The server removes its records and files. Your phone keeps its copy.
- Sign out at any time. The records stay on the phone.
- To delete your account and everything tied to it, write to the contact address above. (An in-app delete account is planned, PLAN.md T081d.)

## Children

The apps are not made for children under 13 and we don't knowingly collect their data.

## Changes

If this policy changes, the new version shows here with a new date, and changes that affect what we collect are mentioned in the app's release notes.
