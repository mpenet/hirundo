# Hirundo + Datastar Demo

A minimal demo showing hirundo's SSE support with [Datastar](https://data-star.dev),
the hypermedia framework.

## What it demonstrates

- **Live Clock** -- streams time updates every second via `datastar-patch-elements`
- **Counter** -- click-triggered SSE stream counting from 1 to 20
- **Live Feed** -- simulated event feed with brotli compression on the SSE stream

## Running

```
cd demo
clj -M -m s-exp.hirundo.demo
```

Then open http://localhost:8080
