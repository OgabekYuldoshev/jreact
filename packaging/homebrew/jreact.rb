# Homebrew formula for a tap repo (OgabekYuldoshev/homebrew-jreact, file Formula/jreact.rb):
#   brew install OgabekYuldoshev/jreact/jreact
# On each release set `version` and `sha256` (shasum -a 256 jreact-cli.jar).
class Jreact < Formula
  desc "JReact: fullstack Java framework (Spring Boot + .jrx UI)"
  homepage "https://github.com/OgabekYuldoshev/jreact"
  version "0.1.0"
  url "https://github.com/OgabekYuldoshev/jreact/releases/download/v#{version}/jreact-cli.jar"
  sha256 "REPLACE_WITH_SHA256_OF_jreact-cli.jar"
  license "Apache-2.0"

  depends_on "openjdk"

  def install
    libexec.install "jreact-cli.jar"
    (bin/"jreact").write <<~SH
      #!/bin/sh
      exec "#{Formula["openjdk"].opt_bin}/java" -jar "#{libexec}/jreact-cli.jar" "$@"
    SH
  end

  test do
    assert_match "Usage", shell_output("#{bin}/jreact --help")
  end
end
