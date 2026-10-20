# Mock GPS – Android app

Map par jagah chuno → START dabao → phone ki saari apps (Maps, WhatsApp, etc.) wahi location dekhengi.

## APK kaise banayein (free, bina PC setup ke)
1. GitHub par naya repository banayein aur is folder ki saari files upload karein (`.github` folder bhi).
2. Repo me **Actions** tab kholein → "Build APK" workflow apne-aap chalega (~5 min).
3. Build complete hone par us run ko kholein → neeche **Artifacts** me `MockGPS-apk` download karein.
4. Zip ke andar `app-debug.apk` phone me install karein ("Install unknown apps" allow karna padega).

Ya Android Studio me folder open karke **Build → Build APK(s)**.

## Phone par ek baar setup
1. Settings → About phone → **Build number** par 7 baar tap (Developer options ON).
2. Settings → Developer options → **Select mock location app** → **Mock GPS**.
3. App kholo, location permission do, jagah chuno, START.

## Features
- Map par tap / marker drag karke location chunein
- Naam se search (OpenStreetMap, koi API key nahi)
- Lat/Lng manually type karein
- Background me chalta hai; notification se STOP
- GPS, Network aur Google Fused – teeno providers ko fake location milti hai

## Dhyan dein
- Kuch apps (banking, kuch games, attendance apps) mock location detect kar leti hain – unme ye kaam nahi karega.
- STOP dabate hi asli GPS wapas aa jata hai.
"# MockGPS" 
