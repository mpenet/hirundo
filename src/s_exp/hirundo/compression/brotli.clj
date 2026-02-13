(ns s-exp.hirundo.compression.brotli
  (:import (com.aayushatharva.brotli4j Brotli4jLoader)
           (com.aayushatharva.brotli4j.encoder BrotliOutputStream
                                               Encoder$Parameters
                                               Encoder$Mode)
           (java.io OutputStream)))

(set! *warn-on-reflection* true)

(defonce ^:private ensure-available
  (delay (Brotli4jLoader/ensureAvailability)))

(defn output-stream
  "Wraps `os` in a BrotliOutputStream configured for TEXT mode.
  Options:
    :quality     - compression quality (0-11)
    :window-size - LZ77 window size (10-24)"
  ^OutputStream [^OutputStream os {:keys [quality window-size buffer-size
                                          encoder-mode]
                                   :or {window-size 24
                                        quality 5
                                        buffer-size 16384 ; https://github.com/andersmurphy/hyperlith/blob/master/src/hyperlith/impl/brotli.clj#L37C5-L37C10
                                        encoder-mode Encoder$Mode/TEXT}}]
  @ensure-available
  (let [params (cond-> (Encoder$Parameters.)
                 quality (.setQuality (int quality))
                 window-size (.setWindow (int window-size))
                 true (.setMode encoder-mode))]
    (BrotliOutputStream. os params buffer-size)))
