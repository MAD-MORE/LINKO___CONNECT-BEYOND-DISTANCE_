# Production connect runbook (initial)

This runbook describes how to run the initial production-grade components locally for testing. It is NOT a full production deployment but provides the artifacts and manifests used to deploy to a VM or k8s cluster.

1. Build images and run compose (staging/dev):
   docker-compose -f infra/docker-compose.yml up --build

2. Provision DNS and TLS for signaling.example.com (or your hostname). Update android ProdConfig with the hostname.

3. Configure coturn by replacing static-auth-secret in infra/coturn/turnserver.conf and setting external-ip if needed.

4. Deploy images to your target (VM/k8s) using the Dockerfiles/Helm stubs (Helm stubs will be added in follow-up commits).
