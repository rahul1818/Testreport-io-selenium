# Publish to Maven Central (like `npm publish`)

Maven Central is the public registry for Java libraries — same idea as npmjs.com for Node packages.

After publishing, anyone can use your library with:

```xml
<dependency>
  <groupId>io.github.rahul1818</groupId>
  <artifactId>testreport-io</artifactId>
  <version>1.0.4</version>
</dependency>
```

---

## One-time setup (do this once)

### 1. Create a Sonatype Central account

1. Go to [https://central.sonatype.com/](https://central.sonatype.com/)
2. Sign up / log in (GitHub login works)
3. Open **Account** → create a **User Token** (save username + password)

### 2. Claim your `groupId` namespace

Your library uses **`io.github.rahul1818`** (already set in `pom.xml`).

1. Go to [Central Portal](https://central.sonatype.com/) → **Publish** → **Namespaces**
2. Register namespace: **`io.github.rahul1818`**
3. Verify via your GitHub repo: [rahul1818/Testreport-io-selenium](https://github.com/rahul1818/Testreport-io-selenium)
4. Wait for approval (usually minutes to 1 day)

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
<version>1.0.5</version>
```

Also update the `<scm><tag>` to match your git tag, e.g. `v1.0.5`.

### Step 2 — commit and tag

```bash
git add .
git commit -m "Release 1.0.5"
git tag v1.0.5
git push origin main --tags
```

### Step 3 — build and publish

From `selenium-viewer-main/`:

```bash
mvn clean deploy -Prelease
```

This will compile, attach sources/javadoc JARs, GPG-sign artifacts, and upload to Maven Central.

With `autoPublish=true`, it goes live after validation (usually 10–30 minutes).

### Step 4 — verify on Maven Central

Search: [https://central.sonatype.com/search](https://central.sonatype.com/search)

Look for: `io.github.rahul1818:testreport-io`

Direct link: [central.sonatype.com/artifact/io.github.rahul1818/testreport-io](https://central.sonatype.com/artifact/io.github.rahul1818/testreport-io/1.0.4)

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `401 Unauthorized` | Check Central token in `~/.m2/settings.xml`, server id must be `central` |
| Namespace not approved | Finish GitHub verification in Central Portal |
| GPG sign failed | Install GnuPG, set `gpg.keyname` in settings |
| Javadoc errors | Already disabled strict lint via `<doclint>none</doclint>` |
| `mvn` not found | Add Maven to PATH or use full path to `mvn.cmd` |
