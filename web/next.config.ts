import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /*
   * Pin Turbopack's root to this folder.
   *
   * Without it, Next walks up looking for a lockfile, finds an unrelated one in a parent
   * directory outside the git repo, and warns on every build. Being explicit also means
   * the build behaves the same wherever the repo is checked out.
   */
  turbopack: {
    root: __dirname,
  },

  /*
   * Next 16 auto-generates AGENTS.md and CLAUDE.md inside web/ on dev runs. The real
   * project guide lives at the repo root, and a second, machine-written CLAUDE.md one
   * directory down competes with it and misleads future sessions. Off.
   */
  agentRules: false,

  /*
   * /careloop.apk is the download button's target, and it is a redirect rather
   * than a file in this repo.
   *
   * The APK is 23MB. Committing one on every build would add 23MB to git
   * history permanently, and history is forever. CI publishes each main build
   * to a rolling GitHub release tag instead, which on a public repo downloads
   * with no login, and this keeps the URL we hand out first-party so the
   * hosting can move later without breaking the link anyone has saved.
   *
   * Not permanent: the destination is expected to change, and a 308 would be
   * cached by browsers long after it did.
   */
  async redirects() {
    return [
      {
        source: '/careloop.apk',
        destination:
          'https://github.com/AdamACE9/careloop/releases/download/latest-apk/careloop.apk',
        permanent: false,
      },
    ];
  },
};

export default nextConfig;
