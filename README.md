# HydroSync Android SMS Gateway App

This Android Studio project turns any Android phone with a SIM card into an automatic **SMS-to-Web-API Gateway** for HydroSync.

## How It Works
1. A user sends an SMS (e.g. `LEVEL` or `STATUS`) to the Android phone.
2. `SmsGatewayReceiver` captures the incoming SMS sender number and message body.
3. `WebhookDispatcher` sends a `POST` request to your HydroSync endpoint (`/api/sms/incoming`) with:
   ```json
   {
     "phone": "+9476...",
     "message": "LEVEL",
     "secret": "your_optional_secret"
   }
   ```
4. HydroSync looks up live tank water levels and dispatches the formatted SMS reply back to the requester.

## Build & Install (Android Studio)
1. Open the `android-sms-gateway` folder in **Android Studio** (Koala / Ladybug or newer).
2. Let Gradle sync automatically.
3. Connect your Android phone via USB (with USB Debugging enabled) or click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
4. Install and open **HydroSync SMS Gateway** on your phone.
5. Tap **Grant SMS Access** and **Keep Alive (Battery)**.
6. Verify your Webhook URL (`https://mwater.vercel.app/api/sms/incoming`) and optional Secret Key, then tap **Test API (Preview)** to confirm connectivity.
