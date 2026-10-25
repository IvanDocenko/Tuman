# Third-party notices

Complete notices and license texts are in `licenses/` and are bundled in `app/src/main/assets/licenses/`. Users can read them offline from About → Open-source licenses.

- `NOTICE.txt`: exact runtime dependency inventory, source availability and Gecko modifications.
- `Mozilla-original.html`: verbatim notice from the pinned GeckoView omni.ja.
- `Mozilla-notices.txt`: readable text rendering of that notice, retaining external links.
- `Apache-2.0.txt`, `MPL-2.0.txt`: complete license texts.
- `Dependency-notices.txt`: original notice/license files found in runtime AAR/JAR archives, deduplicated by content.

GeckoView's MPL-covered source is available at https://hg.mozilla.org/mozilla-central/rev/006163118837775b4ae627815acf6b8d32938271. The source package includes the build-time Gecko transformation in `buildSrc/src/main/java/com/tuman/build/`. Existing dependency headers and notices are retained. This notice does not choose a license for the owner's original Tuman code.
