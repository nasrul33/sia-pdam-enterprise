# Infrastruktur SIA-PDAM

Dokumen operasional production baseline:

- [Deployment](DEPLOYMENT.md)
- [Backup dan restore](BACKUP-RESTORE.md)
- [Rollback](ROLLBACK.md)
- [Observability dan alert](OBSERVABILITY.md)
- [Realm dan Compose smoke Keycloak](keycloak/)

Jangan simpan secret, token, backup, dump database, private key, atau credential produksi di repository. File realm Keycloak hanya berisi identitas dan credential deterministik khusus smoke test yang tidak boleh dipakai di environment lain.
