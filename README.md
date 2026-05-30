# Kolo Finance

Gestion de fonds confiés via WhatsApp. MVP multi-tenant (Spring Boot + Supabase + WhatsApp Cloud API + OpenRouter).

## Prérequis

- Java 17+ (`brew install openjdk@17`)
- Maven 3.6+
- Docker
- Compte [Supabase](https://supabase.com)
- Compte [Meta Business](https://developers.facebook.com) avec WhatsApp Cloud API
- Clé [OpenRouter](https://openrouter.ai)

## Configuration rapide

```bash
cp .env.example .env
# Éditer .env avec vos identifiants
```

### Où trouver les identifiants

| Service | Variable | Où la trouver |
|---------|----------|---------------|
| Supabase | `SUPABASE_DB_URL` | Dashboard Supabase → Settings → Database → Connection string (JDBC) |
| Supabase | `SUPABASE_DB_PASSWORD` | Le mot de passe choisi à la création du projet |
| WhatsApp | `WHATSAPP_TOKEN` | Meta Developer → App → WhatsApp → API Setup → Temporary access token |
| WhatsApp | `WHATSAPP_PHONE_NUMBER_ID` | Meta Developer → App → WhatsApp → API Setup → Phone number ID |
| WhatsApp | `WHATSAPP_VERIFY_TOKEN` | Vous le choisissez (même valeur dans .env et dans la config webhook Meta) |
| OpenRouter | `OPENROUTER_API_KEY` | OpenRouter → Settings → Keys |
| OpenRouter | `OPENROUTER_MODEL` | Modèle IA utilisé pour les messages ambigus |
| App | `APP_API_KEY` | Vous le choisissez (pour sécuriser les endpoints REST) |

## Lancer en dev

```bash
# Sans Docker (Java 17 requis localement)
mvn spring-boot:run

# Avec Docker
docker compose up --build
```

## Déployer sur VPS

```bash
# 1. Cloner sur le VPS
git clone <votre-repo> kolo-finance
cd kolo-finance

# 2. Configurer
cp .env.example .env
nano .env  # remplir les identifiants

# 3. Lancer
docker compose up -d --build

# 4. Configurer nginx (reverse proxy HTTPS)
# Nécessaire pour le webhook WhatsApp
```

### Exemple config nginx

```nginx
server {
    listen 443 ssl;
    server_name api.kolofinance.com;

    ssl_certificate /etc/letsencrypt/live/api.kolofinance.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.kolofinance.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Configurer le webhook WhatsApp

1. Aller sur Meta Developer → App → WhatsApp → Configuration
2. Callback URL : `https://api.kolofinance.com/api/webhook`
3. Verify token : la valeur de `WHATSAPP_VERIFY_TOKEN` dans votre `.env`
4. S'abonner aux messages

## API REST

Tous les endpoints (sauf webhook) nécessitent le header `X-API-Key`.

### Organisations

```bash
# Créer
curl -X POST http://localhost:8080/api/organizations \
  -H "X-API-Key: votre-cle" \
  -H "Content-Type: application/json" \
  -d '{"name": "Mon Entreprise"}'

# Lister
curl http://localhost:8080/api/organizations -H "X-API-Key: votre-cle"
```

### Utilisateurs

```bash
# Créer un agent
curl -X POST http://localhost:8080/api/organizations/1/users \
  -H "X-API-Key: votre-cle" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "22370000000", "name": "Moussa", "role": "AGENT"}'
```

### Fonds

```bash
# Confier des fonds à un agent
curl -X POST http://localhost:8080/api/organizations/1/funds \
  -H "X-API-Key: votre-cle" \
  -H "Content-Type: application/json" \
  -d '{"agentId": 1, "amount": 500000, "description": "Fonds chantier Faladié"}'
```

### Dashboard

```bash
curl http://localhost:8080/api/organizations/1/dashboard -H "X-API-Key: votre-cle"
```

## Flow WhatsApp

```
Agent envoie: "depense 25000 carburant"
  → Bot: "📝 Confirmer dépense de 25 000 FCFA pour carburant ?"

Agent répond: "oui"
  → Bot: "✅ Dépense confirmée : 25 000 FCFA pour carburant. 💰 Solde restant : 475 000 FCFA"
```

## Structure

```
src/main/java/com/kolofinance/
├── config/          # Sécurité, CORS
├── controller/      # REST + Webhook
├── dto/             # Objets de transfert
├── model/           # Entités JPA
├── repository/      # Accès données
└── service/         # Logique métier
```
