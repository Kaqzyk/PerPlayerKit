# PerPlayerKit documentation

The source for [perplayerkit.com](https://perplayerkit.com), built with [Mintlify](https://mintlify.com).

## Local preview

```bash
npm i -g mint     # or use npx mint@latest
cd docs
mint dev          # http://localhost:3000
```

Useful checks before pushing:

```bash
mint broken-links --check-anchors --check-redirects
mint validate
```

## Layout

```
docs/
├── docs.json              # site config + sidebar navigation
├── introduction.mdx       # landing page
├── installation.mdx
├── quickstart.mdx
├── configuration/         # one page per config.yml section
├── commands/              # command and permission reference
├── guides/                # task-oriented walkthroughs
├── api/                   # Java API for plugin developers
├── images/                # screenshots
├── logo/                  # navbar logos (light/dark)
└── .mintlify/AGENTS.md    # instructions for the Mintlify agent
```

Adding a page means creating the `.mdx` file **and** listing it in `docs.json` under
`navigation.groups`. A page not listed there is unreachable from the sidebar.

## Source of truth

Config keys, commands, and permissions must match the plugin, not older docs. Verify against:

- `src/main/resources/config.yml`
- `src/main/resources/plugin.yml`
- `src/main/resources/lang/en.yml`

## Dashboard setup

These live in the [Mintlify dashboard](https://dashboard.mintlify.com), not in this repo:

| What                | Where                                                                      |
| ------------------- | -------------------------------------------------------------------------- |
| GitHub deployment   | Settings → GitHub. Point the deployment at this repo with `docs/` as the content directory, so every push to `main` redeploys. |
| CI checks           | Add-ons → enable **Broken links** (and optionally **Vale**) at Warning or Blocking level. Runs on PRs. |
| AI automations      | Automations → create a run triggered on repository push or a schedule. Point it at this repo so the agent opens a PR when the plugin's config or commands change. |
| Assistant           | Add-ons → Assistant. Answers reader questions using these pages as context. |

The agent reads `.mintlify/AGENTS.md` for project conventions and accuracy rules — update
that file when a new recurring mistake shows up, so automated PRs stop repeating it.

`docs.json` already enables the reader-facing AI features: the contextual menu (copy page,
open in ChatGPT/Claude/Perplexity, install the MCP server in Cursor or VS Code) and search
indexing. `llms.txt` and the MCP server are generated automatically on deploy.
