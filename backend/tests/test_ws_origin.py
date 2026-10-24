from __future__ import annotations

from app.ws import origin_allowed


def test_origin_allowed_same_host_and_default_ports():
    assert origin_allowed(None, "inbox.local", ())
    assert origin_allowed("https://inbox.example.com", "inbox.example.com:443", ())
    assert origin_allowed("http://127.0.0.1:8787", "127.0.0.1:8787", ())
    assert not origin_allowed("https://evil.example", "inbox.example.com", ())
    assert origin_allowed(
        "https://inbox.example.com",
        "inbox.example.com",
        ("https://inbox.example.com",),
    )
    assert not origin_allowed(
        "https://other.example.com",
        "inbox.example.com",
        ("https://inbox.example.com",),
    )
