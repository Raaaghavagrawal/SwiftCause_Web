# SwiftCause Android Kiosk App

Android kiosk application for SwiftCause donations.

## Setup

### Prerequisites

- Android Studio Ladybug or newer
- JDK 11 or newer
- Android SDK 24+ (minSdk)
- Android SDK 36 (targetSdk)

### Configuration

#### Firebase Configuration

Ensure `google-services.json` is present in `app/` directory:
```
android/app/google-services.json
```

This file is gitignored. Download it from:
- Firebase Console → Project Settings → General → Your apps → Download `google-services.json`

#### Stripe Configuration

The Stripe publishable key is hardcoded in `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"pk_test_...\"")
```

**Note:** Stripe publishable keys are safe to commit (they're public by design, used in client apps).

For production, update the key in `build.gradle.kts` to use the live key.

### Building

```bash
./gradlew assembleDebug      # Debug build
./gradlew assembleRelease    # Release build
```

### Running

1. Open the `android` directory in Android Studio
2. Sync Gradle files
3. Run on emulator or connected device

### Project Structure

```
app/src/main/java/com/example/swiftcause/
├── MainActivity.kt                    # App entry point
├── data/
│   ├── models/
│   │   ├── Campaign.kt               # Campaign data model
│   │   └── PaymentModels.kt          # Payment request/response models
│   └── repository/
│       ├── CampaignRepository.kt     # Firestore campaign data
│       └── PaymentRepository.kt      # Firebase Functions payment API
├── domain/
│   └── models/                       # Domain models
├── presentation/
│   ├── screens/
│   │   ├── KioskLoginScreen.kt      # Login with kiosk code
│   │   ├── CampaignListScreen.kt    # Browse campaigns
│   │   └── CampaignDetailsScreen.kt # Campaign details & donate
│   └── viewmodels/
│       ├── CampaignListViewModel.kt
│       └── PaymentViewModel.kt       # Payment flow state
├── ui/
│   ├── components/                   # Reusable UI components
│   └── theme/                        # Material 3 theme
└── utils/
    ├── CurrencyFormatter.kt
    └── StripeConfig.kt               # Stripe SDK initialization
```

## Features

### Campaign Management
- ✅ Kiosk authentication via organization code
- ✅ Real-time campaign list from Firestore
- ✅ Campaign details with image carousel
- ✅ Rich text description rendering (HTML + legacy format)
- ✅ Progress tracking and goal visualization

### Payment Processing
- ✅ Stripe PaymentSheet integration
- ✅ One-time donations
- ✅ Recurring donations (monthly/yearly)
- ✅ Multiple currency support
- ✅ Anonymous donations (kiosk mode)
- ✅ 3D Secure / SCA support
- ⏳ Tap-to-pay (Phase 2)

### UI/UX
- ✅ Material 3 design
- ✅ Skeleton loaders for images
- ✅ Image caching with Coil
- ✅ Dark mode support
- ✅ Responsive layouts

## Testing Payments

### Test Cards (Stripe)

| Card Number         | Scenario              |
|--------------------|-----------------------|
| 4242 4242 4242 4242 | Success              |
| 4000 0000 0000 0002 | Decline              |
| 4000 0027 6000 3184 | Requires Auth (3DS)  |
| 4000 0000 0000 9995 | Insufficient Funds   |

- **Expiry:** Any future date
- **CVC:** Any 3 digits
- **ZIP:** Any valid ZIP code

### Test Flow

1. Build and run app
2. Login with kiosk code: `TEST123`
3. Select a campaign
4. Enter donation amount
5. Tap "Donate"
6. PaymentSheet appears
7. Enter test card: `4242 4242 4242 4242`
8. Complete payment
9. Success toast appears

## Dependencies

### Core
- Kotlin 2.0.21
- Jetpack Compose (BOM 2024.09.00)
- Material 3

### Firebase
- Firebase Auth
- Firebase Firestore
- Firebase Functions

### Payments
- Stripe Android SDK 20.49.0

### Networking
- Retrofit 2.11.0
- OkHttp 4.12.0

### Image Loading
- Coil 2.7.0

### State Management
- ViewModel
- StateFlow
- Kotlin Coroutines

## Architecture

The app follows **Clean Architecture** with MVVM pattern:

- **Presentation Layer:** Composables + ViewModels
- **Domain Layer:** Business models and use cases
- **Data Layer:** Repositories, data sources (Firestore, Firebase Functions)

## Known Issues

1. **YouTube Video Playback**
   - Some YouTube videos fail to play in WebView (Error 152)
   - This is a YouTube restriction for embedded players
   - Workaround: Show thumbnail only for restricted videos

## Roadmap

### Phase 1: Core Features ✅
- [x] Kiosk authentication
- [x] Campaign browsing
- [x] Campaign details
- [x] Card payment integration (PaymentSheet)

### Phase 2: Enhanced Payments
- [ ] Tap-to-pay via Stripe Terminal SDK
- [ ] NFC card reader support
- [ ] Donor information collection
- [ ] Email receipts
- [ ] Payment confirmation screen

### Phase 3: Kiosk Features
- [ ] Physical receipt printing
- [ ] Multi-language support
- [ ] Accessibility enhancements
- [ ] Offline mode support

## Contributing

Please ensure:
1. Code follows Kotlin coding conventions
2. All new features have proper error handling
3. ViewModels manage UI state with StateFlow
4. UI is responsive and accessible
5. No secrets committed to version control

## Support

For issues or questions:
- GitHub Issues: [SwiftCause_Web/issues](https://github.com/your-org/SwiftCause_Web/issues)
- Documentation: `/docs/`
