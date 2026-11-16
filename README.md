<div align="center">

<img src="docs/skypass-icon.png" alt="Skypass app icon" width="16%" />

# Skypass
### Search a flight, choose every seat, and watch your boarding pass print

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2026+-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Supabase](https://img.shields.io/badge/Supabase-Auth%20%C2%B7%20Postgres%20%C2%B7%20Realtime-3FCF8E?style=flat-square&logo=supabase&logoColor=white)](https://supabase.com/)
[![Hilt](https://img.shields.io/badge/Hilt-DI-FF6F00?style=flat-square&logo=android&logoColor=white)](https://dagger.dev/hilt/)
[![Architecture](https://img.shields.io/badge/MVVM-SOLID%20%C2%B7%20multi--module-0F5B56?style=flat-square)](#architecture)
[![Tests](https://img.shields.io/badge/Tests-JUnit%205%20%C2%B7%20Turbine-1C2550?style=flat-square&logo=junit5&logoColor=white)](#tests)

<br />

<img src="docs/gifs/skypass-demo.gif" alt="Skypass demo: animated onboarding and search, the boarding pass printing, and Explore" width="100%" />

</div>

---

## Overview

**Skypass** is a native Android flight-booking app, built with Kotlin and Jetpack Compose by [AhmadALSaffan](https://github.com/AhmadALSaffan). A traveller searches a real-looking global network, compares results laid out as boarding passes, fills in their passport details, picks a seat on a full aircraft map, pays, and watches the boarding pass **print out of a printer** on screen.

Everything behind it runs on **Supabase**:

- a year of departures (924,000 flights across 110 airports and 48 airlines);
- seat maps generated per aircraft;
- prices, promo codes and bookings computed on the server;
- row-level security on everything a traveller owns.

The app is split into feature and core modules, follows **MVVM** with use cases and small repository interfaces (**SOLID**), and has a light and a dark theme.

---

## Screenshots

Every screen below is shown in **light** and **dark**. The phone picks the theme.

### First run: an animated onboarding

Each page is a short scene played in Compose, not a video. A route draws itself and a boarding pass drops onto it. A result unfolds into a ticket, with its barcode scanning and its price counting up. Then a gate-change alert lands on a phone that's offline.

| | Find flights | Every result is a pass | In your pocket | Sign in |
|:---:|:---:|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/onboarding-flights.jpg" width="170"> | <img src="docs/screenshots/light/onboarding-pass.jpg" width="170"> | <img src="docs/screenshots/light/onboarding-pocket.jpg" width="170"> | <img src="docs/screenshots/light/signin.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/onboarding-flights.jpg" width="170"> | <img src="docs/screenshots/dark/onboarding-pass.jpg" width="170"> | <img src="docs/screenshots/dark/onboarding-pocket.jpg" width="170"> | <img src="docs/screenshots/dark/signin.jpg" width="170"> |

### Search

| | Home | Your next trip | Results |
|:---:|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/home.jpg" width="170"> | <img src="docs/screenshots/light/home-trip.jpg" width="170"> | <img src="docs/screenshots/light/results.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/home.jpg" width="170"> | <img src="docs/screenshots/dark/home-trip.jpg" width="170"> | <img src="docs/screenshots/dark/results.jpg" width="170"> |

### Book

| | Seat map | Passenger details | Payment |
|:---:|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/booking.jpg" width="170"> | <img src="docs/screenshots/light/passenger.jpg" width="170"> | <img src="docs/screenshots/light/payment.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/booking.jpg" width="170"> | <img src="docs/screenshots/dark/passenger.jpg" width="170"> | <img src="docs/screenshots/dark/payment.jpg" width="170"> |

### The boarding pass prints

After payment, a printer slot slides in and the pass feeds out of it line by line, with a haptic tick on each step. When it's done, the pass tears free with a spring. Then you can add it to Google Wallet or share it.

<div align="center">
<img src="docs/gifs/boarding-pass-printing.gif" alt="Paying, then the boarding pass feeding out of a printer and tearing free" width="36%" />
</div>

| | Printing | Your boarding pass |
|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/printing.jpg" width="170"> | <img src="docs/screenshots/light/boarding-pass.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/printing.jpg" width="170"> | <img src="docs/screenshots/dark/boarding-pass.jpg" width="170"> |

### Explore

The tiles, the deal and the photo grid are for inspiration. Tapping a destination, an airport or the deal fills Home's search card and switches to it.

<div align="center">
<img src="docs/gifs/explore.gif" alt="Explore: filtering by category, then the deal filling the search card with JFK to Auckland" width="34%" />
</div>

| | Explore | A category | Search |
|:---:|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/explore.jpg" width="170"> | <img src="docs/screenshots/light/explore-beaches.jpg" width="170"> | <img src="docs/screenshots/light/explore-search.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/explore.jpg" width="170"> | <img src="docs/screenshots/dark/explore-beaches.jpg" width="170"> | <img src="docs/screenshots/dark/explore-search.jpg" width="170"> |

### Hotels

Stays in the city you're flying to, from the day you land. Every city on the network has stays. Pick a room, then add the lead guest, arrival time and requests; the server prices and books it.

| | Hotels | Choose a room | Guest details | Booked |
|:---:|:---:|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/hotels.jpg" width="170"> | <img src="docs/screenshots/light/hotel-rooms.jpg" width="170"> | <img src="docs/screenshots/light/hotel-guest.jpg" width="170"> | <img src="docs/screenshots/light/hotel-reserved.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/hotels.jpg" width="170"> | <img src="docs/screenshots/dark/hotel-rooms.jpg" width="170"> | <img src="docs/screenshots/dark/hotel-guest.jpg" width="170"> | <img src="docs/screenshots/dark/hotel-reserved.jpg" width="170"> |

### Car rental

Pick up at the airport you land at. Choose a type, then a car, then the driver and extras; the total follows your days, extras and where you drop it off.

| | Rent a car | One way | Driver & extras | Reserved |
|:---:|:---:|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/cars.jpg" width="170"> | <img src="docs/screenshots/light/cars-oneway.jpg" width="170"> | <img src="docs/screenshots/light/car-checkout.jpg" width="170"> | <img src="docs/screenshots/light/cars-reserved.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/cars.jpg" width="170"> | <img src="docs/screenshots/dark/cars-oneway.jpg" width="170"> | <img src="docs/screenshots/dark/car-checkout.jpg" width="170"> | <img src="docs/screenshots/dark/cars-reserved.jpg" width="170"> |

### My trips

Every flight, stay and car you've booked, kept in Supabase so it follows you to another phone. Open one for everything you entered when booking, directions, and free cancellation while it lasts.

| | My trips | A stay |
|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/trips.jpg" width="170"> | <img src="docs/screenshots/light/trip-stay.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/trips.jpg" width="170"> | <img src="docs/screenshots/dark/trip-stay.jpg" width="170"> |

### Profile and Saved

Your tier and travel stats, counted on the server from your own bookings, and everything you've saved: flights with today's fare, stays and places.

| | Profile | Personal information | Saved flights | Saved places |
|:---:|:---:|:---:|:---:|:---:|
| **Light** | <img src="docs/screenshots/light/profile.jpg" width="170"> | <img src="docs/screenshots/light/profile-info.jpg" width="170"> | <img src="docs/screenshots/light/saved.jpg" width="170"> | <img src="docs/screenshots/light/saved-places.jpg" width="170"> |
| **Dark** | <img src="docs/screenshots/dark/profile.jpg" width="170"> | <img src="docs/screenshots/dark/profile-info.jpg" width="170"> | <img src="docs/screenshots/dark/saved.jpg" width="170"> | <img src="docs/screenshots/dark/saved-places.jpg" width="170"> |

---

## Features

- 🎬 **Animated onboarding**: three scenes driven by one timeline per page (`rememberTimeline`, `appear`, `pop`, `rise`, `typed`). A page replays when it becomes active. Previews and reduced motion jump to the final frame.
- 🔐 **Sign in**: Google through Credential Manager, exchanged for a Supabase session (ID token + nonce), or continue as a guest (anonymous sign-in).
- 🏠 **Home**:
  - **From** is the airport nearest to you, if you allow location.
  - The swap button turns half a turn.
  - Pick an airport from a searchable sheet and dates from a calendar.
  - **+ Add return** opens the calendar; it never guesses a date.
  - Cabin is a dropdown.
  - Your next trip shows a live gate and status through Supabase Realtime.
  - Popular destinations from your airport, with photos.
- ✈️ **Takeoff**: the search icon turns into a plane, grows, and flies across the screen while the results slide in.
- 🎫 **Results as boarding passes**:
  - A five-day price strip.
  - Cheapest, Fastest and Earliest sorting, each a strategy class.
  - Filters for stops, airlines and departure time.
  - Airline logos, a "Best value" pill, and saving a flight.
  - Cards expand to show perks and the price for everyone travelling.
- 💺 **The whole aircraft**: the server builds the seat map from the aircraft type (3-3, 3-4-3, 2-4-2 and more), with legroom rows and seats taken. Every traveller gets a seat; you start with seats side by side, and tapping a fellow traveller's seat swaps you.
- 🛂 **Passport details**: all nine fields the airline needs: first and last name, gender, passport type, number, issue and expiry date, phone and email. They're checked as you type: an expiry before the trip, or within six months of it, is flagged.
- 💳 **Payment**:
  - The card preview flips when you change method.
  - Promo codes are validated on the server.
  - The price summary and the total animate as they change.
  - Paying is idempotent: a retry after a network error reuses the same booking, so nothing is charged twice.
- 🖨️ **Boarding pass**: printed on screen, then shown with a real QR code (zxing) of the PNR at full brightness. **Add to Google Wallet** carries the official mark (issuing a real pass needs a Wallet issuer account), and you can share the pass.
- 🧭 **Explore**:
  - Service tiles for Flights, Hotels and Car rental.
  - A fare deal with its photo.
  - Category chips and a two-column photo grid, priced from your own airport.
  - Search covers cities, countries and airports.
  - The search and the chosen chip survive process death.
- 🏨 **Hotels**:
  - Opens on the arrival city of your next trip, from the day you land, for a week; change the city, dates and guests in one sheet.
  - All, Under $250, 5-star and Free cancel chips.
  - Cards show the rating, stars, amenities, the nightly rate and the total for your stay.
  - Hearts save a hotel, with a little bounce.
  - Standard, Deluxe and Suite rooms, offered only when they fit your party.
  - Guest details: the lead guest (prefilled from your saved passenger), arrival time, requests such as a high floor or a quiet room, and a note. Check-in and check-out times, the full price and the cancellation policy before you book.
  - Reserving is priced on the server and idempotent, like flights. The map button opens the city's hotels in your maps app.
- 🚗 **Car rental**:
  - Picks up at the arrival airport of your next trip at 14:00 on the day you land, back at 10:00 a week later.
  - Change the pick-up airport (any on the network), the dates, or return it somewhere else: the city centre or another airport in the country, for a one-way fee.
  - Compact, SUV, Electric and Premium. Each car stands on its own colour with a soft shadow, and the selected one settles onto it.
  - The total counts up as you change car, days or drop-off, and the server's price matches it. A reserved car drives into the confirmation.
  - Driver & extras: the main driver (prefilled), their age band (under 25 adds a young driver fee), the arriving flight number (suggested from your next trip, so the desk waits if it's late), and extras priced per day: full protection, child seat, GPS, additional driver.
- 🧳 **My trips**:
  - Flights, stays and cars in tabs, upcoming first, then past and cancelled.
  - A flight opens its boarding pass. A stay or car opens every detail you entered, the price breakdown and directions.
  - Free cancellation from the app, with an "are you sure" step, until the day before check-in or 48 hours before pick-up. The server enforces the same rule.
- 👤 **Profile**:
  - Your tier (Blue, Silver at 10k miles, Gold at 25k, Platinum at 50k) with a bar to the next one, and Trips, Countries and Miles tiles that count up. Miles are the great-circle distance of the flights you've booked, worked out in Postgres.
  - Personal information: change your name and home airport (Explore and Saved price fares from it). Column privileges on `profiles` mean the app can't touch the tier or miles.
  - Passengers & travel documents: add, edit and remove travellers with the same passport form booking uses, with a warning when a passport expires within six months.
  - Badges update live: the saved count follows every heart and bookmark in the app.
  - Payment methods and a help centre with quick answers.
- 🔖 **Saved**:
  - Flights (with today's economy fare, and Departed once they've gone), stays and places, in tabs.
  - A saved flight opens booking, a stay opens hotels in its city, and a place fills Home's search.
  - Remove with Undo. Explore's destination tiles now have hearts too.
- 💳 **Payment methods**:
  - Add Visa, Mastercard, American Express and Discover cards: the brand is recognised from the first digits, the number is grouped as printed (4-6-5 for Amex) and checked with Luhn, expiry and CVC rules, and a live card fills in as you type.
  - Only the brand, last four digits, expiry and name are stored; the full number and CVC never leave the phone. The server checks the card is yours and unexpired when you pay.
  - The official network marks and Google Pay on white tiles; make a card the default or remove it; add one right at checkout.
- ⚙️ **Settings**:
  - Push, gate & delay and price-drop alerts (asks for Android 13's notification permission), saved on your account.
  - Currency (USD, EUR, GBP, NZ$) applied to every price in the app; checkout says flights are charged in US dollars.
  - Dark mode (System, On, Off), home airport, connected accounts, sign out (guests are warned first).
  - Biometric lock with BiometricPrompt: locks the app on start and after 30 seconds away, and confirms payments.
- ⚡ **Fast start**: the splash screen only waits for the session saved on the phone, never the network; the Supabase client is built off the main thread while the activity starts. Data calls wait for a stale token's refresh on their own.
- 🌗 **Light and dark**: every screen, from the same design tokens.
- ♿ **Every state designed**: loading skeletons, empty, offline and error states with retry.

---

## Architecture

```text
:app ──► :feature:*  ──► :core:domain ◄── :core:data ──► :core:network (supabase-kt)
              │               ▲                 │
              ▼               │                 └──► :core:datastore
      :core:designsystem   :core:model
```

- **MVVM, one direction.** Each screen is a `XRoute` (collects state and one-off effects, gets its ViewModel from Hilt), then a stateless `XScreen(uiState, onEvent)`, then a `XViewModel` holding a `StateFlow` of UI state, a sealed `Event` and a `Channel` of `Effect`s.
- **Single responsibility.** One use case per action: `SearchFlightsUseCase`, `CalculateBookingTotalUseCase`, `ValidatePassengerUseCase`, `PayForTripUseCase`, `GetExploreCatalogUseCase`, `ReserveHotelUseCase`, `PriceRentalUseCase` and more. ViewModels only map state.
- **Open/closed.** Sorting is a `FlightSortStrategy`. The price is a chain of `PriceRule`s (fare, promo, tax, seat fee, bags). Each hotel chip is a rule on `HotelFilter`. A new rule is a new class or entry.
- **Interface segregation.** Small repositories: `FlightSearchRepository`, `SeatMapRepository`, `PassengerRepository`, `BookingRepository`, `ExploreRepository`, `SearchIntentRepository`, `HotelRepository`, `SavedHotelsRepository`, `CarRepository`, `MyTripsRepository`, `ProfileStatsRepository`, `EditProfileRepository`, `SavedListRepository` and more. Shared screen pieces that need both the design system and the domain (the passenger form) live in `:core:ui`. There's no "god" repository.
- **Dependency inversion.** Features see interfaces from `:core:domain` only; Hilt binds the Supabase implementations in `:core:data`. Fakes in `:core:testing` replace them in tests.
- **Server as the source of truth.** Prices, seat holds, promo codes and the PNR are computed in Postgres functions. The app shows the same numbers.

### Backend (Supabase)

| | |
|---|---|
| Tables | `airports`, `airlines`, `routes`, `flights`, `flight_instances`, `cabin_layouts`, `destinations`, `deals`, `promo_codes`, `profiles`, `passengers`, `bookings`, `booking_passengers`, `booking_seats`, `saved_items`, `hotels`, `hotel_reservations`, `cars`, `rental_reservations`, `user_settings` |
| Functions | `search_flights`, `date_price_strip`, `flight_offer`, `get_seat_map`, `create_booking`, `pay_booking`, `check_promo`, `boarding_pass`, `next_trip`, `explore_destinations`, `reserve_hotel`, `reserve_car`, `my_flights`, `cancel_reservation`, `profile_stats`, `miles_between`, `saved_flights`, `saved_hotels`, `saved_destinations`, `set_default_card` |
| Security | Row-level security on every user table; catalogue tables are read-only; functions run with a fixed `search_path` |
| Realtime | `flight_instances` updates, for live gate and status changes |
| Data | 924,067 departures from 2026-11-15 to 2027-11-15, generated in SQL from a compact route list; 5 hotels in every city and 8 rental cars at every airport on the network |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose (BOM 2026.02), Material 3, type-safe Navigation Compose |
| Motion | Timeline-driven Compose animation, `Animatable`, springs, `graphicsLayer` |
| DI | Hilt + KSP |
| Backend | Supabase: Auth (Google ID token, anonymous), Postgrest, Realtime, Storage, via supabase-kt 3.6 on Ktor (OkHttp) |
| Sign-in | Credential Manager + Google ID |
| Images | Coil 3 |
| QR codes | zxing core |
| Local storage | DataStore |
| Build | Gradle convention plugins (`build-logic`), version catalog, AGP 9.5 |
| Tests | JUnit 5, Turbine, kotlinx-coroutines-test, fakes |
| CI | GitHub Actions: unit tests and a debug build on every push |
| Min / target SDK | 26 / 37 |

---

## Getting Started

### Prerequisites

- Android Studio (a recent stable release), JDK 21
- A Supabase project (the free plan is enough)
- A Google Cloud OAuth client (Web application) for Google sign-in

### 1. Clone

```bash
git clone https://github.com/AhmadALSaffan/skypass.git
cd skypass
```

### 2. Database

Apply the migrations in `supabase/migrations/`, in order, with the Supabase CLI:

```bash
supabase link --project-ref <your-project-ref>
supabase db push
```

Migration 7 generates a year of departures, so it can take a while.

### 3. Auth

In the Supabase dashboard, under **Authentication → Sign In / Providers**:

- turn on **Google** with your Web client ID and secret;
- turn on **Allow anonymous sign-ins** for "Continue as guest".

Add your debug keystore's SHA-1 to an **Android** OAuth client in the same Google Cloud project.

### 4. Keys in `local.properties` (git-ignored)

```properties
supabase.url=https://your-project.supabase.co
supabase.key=sb_publishable_...
google.webClientId=1234567890-xxxx.apps.googleusercontent.com
```

### 5. Build and run

```bash
./gradlew :app:installDebug
```

<a id="tests"></a>
### Tests

```bash
./gradlew testDebugUnitTest test
```

The tests cover the ViewModels (with Turbine), the use cases, the price rules, the sort strategies and passport validation, booking-form checks, extras pricing and the My trips split. The promo-code maths is checked against the server's own figure.

---

## Project Structure

```text
app/                    MainActivity, navigation host, the takeoff overlay, Coil setup
build-logic/            convention plugins (application, library, feature, compose, hilt)
core/
├── model/              plain Kotlin models (Flight, Seat, Passenger, Booking, Destination…)
├── domain/             repository interfaces and use cases (pure Kotlin)
├── data/               Supabase repositories, DTOs, Hilt bindings
├── network/            the Supabase client
├── auth/               Google sign-in with Credential Manager
├── datastore/          onboarding and preferences
├── designsystem/       theme, tokens, motion, ticket shapes, icons, components
├── ui/                 shared forms that need the domain (the passenger form)
└── testing/            fakes and test data
feature/
├── onboarding/         the three animated scenes
├── auth/               sign in
├── home/               search card, pickers, next trip, popular destinations
├── results/            boarding-pass results, price strip, filters
├── booking/            passengers and the seat map
├── payment/            methods, promo code, pay
├── confirmation/       the printer and the boarding pass
├── explore/            tiles, deal, categories, destination grid
├── hotels/             stays, filters, the stay sheet, rooms, guest details
├── cars/               the trip card, car types, cars, driver and extras
├── trips/              My trips: flights, stays and cars, details and cancelling
├── profile/            the account card, tier, stats, menu, personal info, travellers
└── saved/              saved flights, stays and places
supabase/migrations/    schema, functions, security, seed data
docs/                   screenshots and GIFs for this README
```

---

## Contributing

Pull requests are welcome. For major changes, please open an issue first.

1. Fork the repo
2. Create your feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "feat: add your feature"`
4. Push the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## Credits

- Destination photos come from [Wikimedia Commons](https://commons.wikimedia.org/), each under its own Creative Commons licence; the author and licence are on each file's page.
- Hotel photos come from [Unsplash](https://unsplash.com/), under the Unsplash License.
- Airline logos are loaded from Kiwi.com's public image CDN and belong to their airlines.
- Air New Zealand's name, wordmark and koru belong to Air New Zealand. This is a non-commercial portfolio project and isn't affiliated with or endorsed by Air New Zealand.
- The Google Wallet mark follows Google's brand guidelines.

---

<div align="center">
  Built with ❤️ by <a href="https://github.com/AhmadALSaffan">Ahmad AlSaffan</a>
</div>
