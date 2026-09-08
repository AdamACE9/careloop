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
};

export default nextConfig;
