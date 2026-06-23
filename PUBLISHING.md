# Publish to Maven Central (like `npm publish`)

Maven Central is the public registry for Java libraries — same idea as npmjs.com for Node packages.

After publishing, anyone can use your library with:

```xml
<dependency>
  <groupId>io.github.rahul1818</groupId>
  <artifactId>testreport-io</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## One-time setup (do this once)

### 1. Create a Sonatype Central account

1. Go to [https://central.sonatype.com/](https://central.sonatype.com/)
2. Sign up / log in (GitHub login works)
3. Open **Account** → create an **User Token** (save username + password)

### 2. Claim your `groupId` namespace

Your library uses **`io.github.rahul1818`** (already set in `pom.xml`). You must prove you own that namespace.

1. Go to [Central Portal](https://central.sonatype.com/) → **Publish** → **Namespaces**
2. Register namespace: **`io.github.rahul1818`**
3. Verify via your GitHub repo: [rahul1818/Testreport-io-selenium](https://github.com/rahul1818/Testreport-io-selenium)
4. Wait for approval (usually minutes to 1 day)

> **Alternative:** If you own the `testreport.io` domain, you can use `io.testreport` instead — but then change `groupId` in `pom.xml` and register that namespace with a DNS TXT record.

### 3. Install tools on your machine

- **Java JDK 11+**
- **Maven 3.9+** — [https://maven.apache.org/download.cgi](https://maven.apache.org/download.cgi)
- **GPG** (for signing) — [https://gnupg.org/download/](https://gnupg.org/download/)

### 4. Create a GPG key (required by Maven Central)

```bash
gpg --full-generate-key
# Choose RSA, 4096 bits, your name + email
```

List keys and note the key ID:

```bash
gpg --list-secret-keys --keyid-format=long
```

Upload **public** key to key servers:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

### 5. Configure Maven credentials

Create or edit `%USERPROFILE%\.m2\settings.xml` (Windows) or `~/.m2/settings.xml` (Mac/Linux):

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
      <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>gpg</id>
      <properties>
        <gpg.keyname>YOUR_KEY_ID</gpg.keyname>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>

  <activeProfiles>
    <activeProfile>gpg</activeProfile>
  </activeProfiles>
</settings>
```

> Never commit `settings.xml` or tokens to Git.

---

## Every release (like `npm publish`)

### Step 1 — bump version in `pom.xml`

```xml
<version>1.0.1</version>
```

Also update the `<scm><tag>` to match your git tag, e.g. `v1.0.1`.

### Step 2 — commit and tag

```bash
git add .
git commit -m "Release 1.0.1"
git tag v1.0.1
git push origin main --tags
```

### Step 3 — build and publish

From `selenium-viewer-main/`:

```bash
mvn clean deploy -Prelease
```

This will:

1. Compile the library
2. Attach `-sources.jar` and `-javadoc.jar`
3. GPG-sign artifacts
4. Upload to Maven Central (Sonatype)

With `autoPublish=true`, it goes live on Central after validation (usually 10–30 minutes).

### Step 4 — verify on Maven Central

Search: [https://central.sonatype.com/search](https://central.sonatype.com/search)

Look for: `io.github.rahul1818:testreport-io`

Direct link: [central.sonatype.com/artifact/io.github.rahul1818/testreport-io](https://central.sonatype.com/artifact/io.github.rahul1818/testreport-io/1.0.0)

> **Note:** An earlier publish used artifactId `reporter`. That stays on Maven Central forever — use **`testreport-io`** going forward (same code, correct name).

---

## Quick comparison: npm vs Maven

| npm | Maven Central |
|-----|----------------|
| `npm login` | Sonatype Central account + token in `settings.xml` |
| `npm publish` | `mvn deploy -Prelease` |
| `package.json` name/version | `pom.xml` groupId/artifactId/version |
| npmjs.com | search.maven.org / central.sonatype.com |
| npm account | Claim `groupId` namespace |
| (optional) 2FA | GPG signing (required) |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `401 Unauthorized` | Check Central token in `~/.m2/settings.xml`, server id must be `central` |
| Namespace not approved | Finish DNS/GitHub verification in Central Portal |
| GPG sign failed | Install GnuPG, set `gpg.keyname` in settings |
| Javadoc errors | Already disabled strict lint via `<doclint>none</doclint>` |
| `mvn` not found | Add Maven to PATH or use full path to `mvn.cmd` |

---

## Alternative: GitHub Packages (private / simpler)

If you only need hosting without Maven Central approval:

```xml
<distributionManagement>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/rahul1818/Testreport-io-selenium</url>
  </repository>
</distributionManagement>
```

Then `mvn deploy` with a GitHub Personal Access Token in `settings.xml`.

Users add your GitHub Maven repo in their `pom.xml`. This is **not** on public Maven Central search — good for private teams, not ideal for public libraries.

---

## Checklist before first publish

- [ ] Sonatype Central account created
- [ ] Namespace `io.github.rahul1818` verified in Central Portal
- [ ] GPG key created and uploaded to key server
- [ ] `~/.m2/settings.xml` has Central token + GPG config
- [ ] `LICENSE` file present in project
- [ ] `pom.xml` has developers, scm, licenses (already added)
- [ ] Code pushed to GitHub
- [ ] Run `mvn clean deploy -Prelease`
