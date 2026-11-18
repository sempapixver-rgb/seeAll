# seeAll

Cloud-first project: reverse engineering and reconstruction work for a camera/IP-device companion app, done with [REA](https://github.com/morluto/rea).

## Analysis documents

- [Initial iCSee analysis (REA)](docs/icsee-analise-inicial.md) — target identification, signature, permissions, components, architecture, APK inventory.
- [iCSee architecture and flows](docs/icsee-arquitetura-e-fluxos.md) — SDK constants, `DeviceManager`/`MonitorManager`, backends, reconstructed flow, implications for our implementation.

## Target

- APK: `iCSee.apk`, SHA-256 `4ff77acb3b92b096eb844eea5a4edb4286385341ecd4af9061c99bc8f9cfe7bf`
- Package `com.xm.csee`, version `7.9.6_G` (79633)

All analysis is static only. The APK is never executed. No proprietary code, keys, endpoints or resources are copied here — documents describe behavior and contracts only.
