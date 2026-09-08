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
};

export default nextConfig;
