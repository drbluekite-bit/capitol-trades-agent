# Contributing

## Getting started

1. Fork the repo and clone it locally.
2. Copy `.env.example` to `.env` and fill in your API keys.
3. Install dependencies: `pip install -r requirements.txt`
4. Run in paper trading mode first: `python main.py`

## Making changes

- Keep changes focused — one feature or fix per PR.
- Test against the paper trading endpoint before touching live keys.
- Never commit `.env` or real API keys.

## Reporting issues

Open a GitHub issue with:
- What you expected to happen
- What actually happened
- Relevant log output
