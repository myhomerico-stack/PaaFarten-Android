# På farten – Android

Chauffør-/medarbejderapp til **På farten**.

## Mål
- Android 16 / API 36
- Kotlin + Jetpack Compose
- GitHub Actions bygger APK automatisk

## Første moduler
- I dag
- Vagtplan & kalender
- Udlæg & indkøb med kvitteringsfoto
- Profil

Navigator bygges senere som en separat app og skal kun vise dagens kørsel/navigation.

## Build
Workflow: `.github/workflows/build-paafarten.yml`

Ved push til `main` eller manuel kørsel bygges en debug-APK og gemmes som GitHub Actions artifact.
