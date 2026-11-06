# Deploying FactoryGuard to AWS

**Optional. You do not need this to present the project.** Do not create paid AWS resources just to finish the
prototype: demonstrate it locally and present this as a prepared deployment path. Read `docs/SECURITY.md` first:
the guard controls Docker, so a public deployment needs HTTPS and a hardened design that this prototype does not have.

**Status: prepared but not tested.** It needs a real AWS account to try. The pieces are standard
(EC2, Docker, CloudFormation, ECR, CloudWatch, GitHub Actions) but console menus change, so check AWS's
current documentation wherever a step says "check". Until then, everything runs the same on your laptop.

There are **two paths**. Start with Path A.

| | Path A: simple (recommended first) | Path B: automated |
|---|---|---|
| What you do | Launch one server by hand, upload the project, run one script | Create everything from a template, deploy with GitHub Actions |
| Needs IAM users / roles | **No** (works on student and lab accounts) | Access keys for GitHub; a role is created unless you give an existing one |
| Time | About 30 minutes (most of it the first build) | About an hour the first time |
| Best for | Getting it live and doing the demo | Showing infrastructure-as-code and a pipeline |

## Student accounts, credits and cost

- Free plans and student credits usually cover a small server, but **check what your plan includes** and which
  instance sizes it allows. You need **at least 2 GB of memory** (t3.small or larger); a 1 GB server is too small.
- A public IPv4 address, storage and the server itself all use credits while they exist. Create a **budget
  or billing alert** first. When you are not demoing, **stop** the server; when finished, **delete** everything.
- In AWS Academy Learner Lab style accounts you often cannot create IAM users, and sessions end after a few hours
  (check your lab's rules about servers stopping and addresses changing). Use **Path A**.

## Safety rules (both paths)

- The dashboard is plain HTTP. Allow port **3000** only from **your own IP**, never from everyone.
- Never open port **1883** (the MQTT broker).
- Home IP addresses change; if the dashboard stops loading, update the firewall rule to your current IP.

---

## Path A: one server, done by hand

1. **Region and budget.** Sign in, pick a region near you, and set a billing alert.
2. **Launch the server.** EC2 > Launch instance:
   - Name `factoryguard`, image **Ubuntu Server 22.04**.
   - Instance type **t3.small** (or another size with at least 2 GB that your plan allows).
   - Key pair: create one and save the `.pem` file safely.
   - Network settings: allow **SSH (22)** from **My IP**, and add a rule **Custom TCP 3000** from **My IP**.
   - Storage: 20 GB. Launch, then copy the **public IPv4 address**.
3. **Get the project onto the server.** From your computer (Windows 10/11 PowerShell, Mac and Linux all have these):

       scp -i your-key.pem factoryguard.zip ubuntu@<public-ip>:~
       ssh -i your-key.pem ubuntu@<public-ip>

   (Alternative: put the project on GitHub and `git clone` it on the server. The EC2 console's
   **Connect** button also opens a terminal in your browser.)
4. **Start it.** On the server:

       sudo apt-get update && sudo apt-get install -y unzip
       unzip factoryguard.zip && cd factoryguard
       bash deploy/setup-vm.sh

   The script adds swap memory, installs Docker, builds everything and starts it. The first run takes
   10 to 15 minutes on a small server.
5. **Use it.** Open `http://<public-ip>:3000`, create an account and run the demo.
6. **Prove it in the cloud.** On your own computer (the one whose IP you allowed):

       BASE_URL=http://<public-ip>:3000 python3 scripts/e2e.py

   On Windows PowerShell: `$env:BASE_URL="http://<public-ip>:3000"; python scripts/e2e.py`.
   Write the recovery time it prints into your slides: that is a measurement from AWS.
7. **Pause and finish.** EC2 > Instance state > **Stop** when idle (the public address changes when you start it
   again unless you attach an Elastic IP). **Terminate** the instance when you are completely done.

## Path B: template and pipeline

What it creates:

| AWS service | Used for |
|-------------|----------|
| EC2 (Ubuntu, with swap and Docker) | Runs Docker Compose: FactoryGuard, MQTT broker, simulator, database, demo app |
| ECR (3 repositories) | Stores the versioned images: guard, simulator, demo-app v1.0 and v1.1 |
| CloudWatch Logs (`/factoryguard`) | Logs from guard, simulator, broker and database |
| CloudWatch Metrics + Alarms | Throughput, health and incident counts; alarms notify you |
| SNS topic | Sends the alarm emails |
| Security group, Elastic IP | Only your IP reaches SSH and the dashboard; fixed address |
| IAM role (optional) | Pull images, write logs and metrics, with no keys stored on the server |

Steps:

1. **Key pair and your IP** as in Path A (a key pair; your IP written as `<your-ip>/32`).
2. **Create the stack.** CloudFormation > Create stack > Upload a template file >
   `deploy/aws/factoryguard-stack.yaml`. Parameters:
   - `KeyName`, `AllowedCidr` (required).
   - `AlertEmail`: your email to receive alarms (confirm the email AWS sends you).
   - `ExistingInstanceProfileName`: leave empty normally. If your account cannot create IAM roles, enter the
     instance profile you were given (in AWS Academy Learner Lab it is `LabInstanceProfile`).
   - Tick the box allowing IAM resources only if you left the profile empty. Wait for `CREATE_COMPLETE`.
   Outputs give you `ServerAddress`.
3. **Access for GitHub.** IAM > Users > create user `factoryguard-ci`, attach `AmazonEC2ContainerRegistryPowerUser`,
   create an access key. (If your account cannot create IAM users, use Path A.)
4. **GitHub secrets** (repository Settings > Secrets and variables > Actions):

   | Secret | Value |
   |--------|-------|
   | `AWS_ACCESS_KEY_ID` | from step 3 |
   | `AWS_SECRET_ACCESS_KEY` | from step 3 |
   | `AWS_REGION` | the region you used, for example `eu-west-1` |
   | `EC2_HOST` | the `ServerAddress` output |
   | `EC2_USER` | `ubuntu` |
   | `EC2_SSH_KEY` | the full text of the `.pem` file |

5. **Database password (once).** SSH to the server, wait until `docker --version` works (it installs in the first
   minutes), then run `cd ~/factoryguard && echo "POSTGRES_PASSWORD=choose-a-strong-one" >> .env`.
6. **Deploy.** GitHub > Actions > **Deploy to AWS** > Run workflow. It names any missing secret.
7. **Use and prove it**, exactly as Path A steps 5 and 6.

## CloudWatch: logs, metrics and alerts (Path B)

- **Logs:** CloudWatch > Log groups > `/factoryguard`, one stream per service.
- **Metrics:** namespace `FactoryGuard`, dimension `Service=demo-app`: `EventsPerMinute`, `HealthCheckPassing`,
  `IncidentsDetected`. FactoryGuard publishes them by itself when it runs from the AWS compose override.
- **Alarm `factoryguard-incident-detected`:** emails you when FactoryGuard declares an incident (expect the
  email a minute or two after the rollback starts).
- **Alarm `factoryguard-throughput-not-restored`:** emails you if throughput stays below the threshold for
  3 minutes in a row. That means the automatic rollback did not fix it and a human is needed.
- On Path A the same metrics can be turned on by adding `GUARD_CLOUDWATCH_ENABLED=true` and `AWS_REGION=<region>` to
  `.env` **only if** the server has a role that allows `cloudwatch:PutMetricData`; otherwise leave it off.

## If something fails

- **Stack creation fails on the key name or image:** the key pair must be in the same region; check the region selector.
- **Stack cannot create the role:** your account blocks IAM changes. Re-create the stack with
  `ExistingInstanceProfileName` set to the profile your account provides, or use Path A.
- **Workflow cannot connect over SSH:** the firewall only allows your IP and GitHub's servers are not it. Either
  allow port 22 from anywhere for the deploy and close it again, or run the "Deploy to the EC2 server" commands
  by hand from your computer.
- **ECR push denied:** the IAM user lacks the ECR policy, or `AWS_REGION` differs from the stack's region.
- **Dashboard unreachable:** your IP changed; update the firewall rule. Also confirm port 3000 is allowed.
- **Build is killed or containers stop:** not enough memory; use a bigger instance type.
- **"Image for v1.0 was not found":** the demo-app images did not build or pull; run `./run.sh` again (Path A)
  or check the ECR login line from the workflow (Path B).

## Delete everything when you are done

1. Path A: terminate the instance, release any Elastic IP, delete the security group if you made one just for this.
2. Path B: CloudFormation > delete the stack (if it fails on a repository, delete the images inside it first), then delete the
   `factoryguard-ci` access key or user.
3. Look at the billing page the next day to confirm nothing is still running.

## Not included

HTTPS and a custom domain (needs a load balancer and certificate), a managed database (RDS), and container logs
for the demo app (FactoryGuard starts it outside Compose; use `docker logs demo-app` on the server).
