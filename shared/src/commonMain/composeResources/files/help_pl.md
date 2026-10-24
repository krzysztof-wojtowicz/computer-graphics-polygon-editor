# Polygon Editor – dokumentacja

Edytor wielokątów (zadanie 1). Wariant: **prostopadłość** krawędzi, domyślna ciągłość **C1**.

Dokumentacja składa się z czterech części: instrukcja obsługi, przyjęte założenia, model danych i algorytmy.

## 1. Instrukcja obsługi

### Mysz

| Akcja | Działanie |
|---|---|
| LPM na wierzchołku / punkcie kontrolnym / krawędzi | zaznaczenie elementu (szczegóły w panelu po prawej) |
| LPM w pustym miejscu | usunięcie zaznaczenia |
| Przeciągnięcie wierzchołka | wierzchołek podąża za kursorem, reszta sceny dopasowuje się do ograniczeń |
| Przeciągnięcie punktu kontrolnego | zmiana kształtu krzywej Béziera; wierzchołki tego segmentu się nie ruszają (chyba że wymaga tego ograniczenie) |
| Przeciągnięcie krawędzi | krawędź (z jej punktami kontrolnymi) podąża za kursorem |
| Przeciągnięcie wnętrza wielokąta | przesunięcie całego wielokąta |
| Shift + LPM na krawędzi | dodanie drugiej krawędzi do zaznaczenia (do ograniczenia prostopadłości) |
| Dwuklik na krawędzi | wstawienie wierzchołka w środku krawędzi |

Element pod kursorem jest podświetlany na pomarańczowo, zaznaczony – na niebiesko. Kolejność trafiania: wierzchołek, punkt kontrolny, krawędź, wnętrze.

### Klawiatura

| Klawisz | Działanie |
|---|---|
| Delete / Backspace | usunięcie zaznaczonego wierzchołka |
| F1 | otwarcie tej dokumentacji |
| Esc | zamknięcie okna dialogowego |

### Panel boczny

- **Nic nie zaznaczono** – skrócona instrukcja.
- **Krawędź** – długość, kształt, lista ograniczeń (✕ usuwa ograniczenie), przycisk **Set length…** (okno z bieżącą długością), przyciski kształtu **Line / Bézier / Arc**.
- **Dwie krawędzie** (Shift + LPM) – długości, wspólna lista ograniczeń, przycisk **Make perpendicular**.
- **Wierzchołek** – pozycja, ciągłość **G0 / G1 / C1** (niedostępne klasy są wyszarzone, pod spodem podpowiedź dlaczego), **Delete vertex**. Klasa nadana automatycznie ma dopisek „(default)”.
- **Punkt kontrolny** – krawędź, do której należy, i pozycja.
- Zawsze widoczne: wybór algorytmu rysowania odcinków **Library / Bresenham**, przycisk **Help (F1)** i komunikat o błędzie (np. sprzeczne ograniczenie).

### Oznaczenia na scenie

- `L 120.0` – zadana długość krawędzi, `⊥1`, `⊥2`… – prostopadłość (ten sam numer na obu krawędziach pary). Ikony stoją przy środku krawędzi, po zewnętrznej stronie.
- `G1`, `C1` przy wierzchołku – klasa ciągłości (G0 nie jest oznaczane).
- Krzywa Béziera ma przerywany wielobok kontrolny i kwadratowe punkty kontrolne.

### Scena demo

Po uruchomieniu widoczne są trzy wielokąty:

- **łuki** (z lewej) – łuk G0/G0 (półokrąg) i łuk G1 styczny do sąsiedniego odcinka, lewa krawędź prostopadła do górnej krawędzi logo (ograniczenie między wielokątami),
- **logo Kotlina** (w środku) – kilka ograniczeń na jednej krawędzi (lewa: długość i dwie prostopadłości), prostopadłe ramiona wcięcia,
- **„kokardka”** (z prawej) – wielokąt z samoprzecięciem, prostopadłe przecinające się krawędzie, boki Béziera z domyślnym C1 oraz z jawnym G1 i G0.

## 2. Przyjęte założenia

### Ograniczenia

- Ograniczenia (zadana długość, prostopadłość) można nałożyć tylko na **odcinki**. Zmiana kształtu krawędzi na Bézier lub łuk usuwa jej ograniczenia.
- Krawędź może mieć kilka ograniczeń, ale tylko jedną zadaną długość – nowa długość zastępuje poprzednią. Tej samej pary krawędzi nie można dwa razy oznaczyć jako prostopadłej.
- Prostopadłość może łączyć krawędzie **różnych wielokątów** (ograniczenia należą do sceny, nie do wielokąta).
- Ograniczenie, którego nie da się spełnić razem z istniejącymi, jest **odrzucane** – scena wraca do stanu sprzed próby, w panelu pojawia się komunikat.
- Jeśli przeciągnięcia nie da się pogodzić z ograniczeniami, **przesuwany jest cały wielokąt** (fallback). Przesunięcie nie zmienia długości ani kierunków, więc wszystkie ograniczenia pozostają spełnione.

### Wstawianie i usuwanie wierzchołków

- Wstawienie wierzchołka dzieli krawędź na dwa **odcinki**; ograniczenia dzielonej krawędzi są usuwane.
- Usunięcie wierzchołka zastępuje dwie sąsiednie krawędzie jednym **odcinkiem**; ograniczenia obu krawędzi są usuwane.
- Wielokąt ma zawsze co najmniej 3 wierzchołki.

### Ciągłość

- G1 i C1 mają sens tylko obok krzywej: między dwoma odcinkami dostępne jest wyłącznie G0.
- Zamiana krawędzi na Bézier ustawia na jej końcach **domyślne C1**, chyba że użytkownik wcześniej sam wybrał klasę w tym wierzchołku.
- Po każdej zmianie sąsiedztwa (kształt, wstawienie, usunięcie) niedozwolone klasy są obniżane: C1 → G1 → G0.

### Łuki

- Łuk G0/G0 to **półokrąg** o środku w środku krawędzi i promieniu równym połowie jej długości. Wybrzusza się w lewo od kierunku start → koniec, czyli na zewnątrz wielokąta o wierzchołkach podanych zgodnie z ruchem wskazówek zegara na ekranie.
- Łuk może mieć G1 **tylko na jednym końcu** – ustawienie G1 na drugim końcu przełącza pierwszy na G0.
- Łuk zawsze dopasowuje się do sąsiada (styczna sąsiada wyznacza łuk), nigdy odwrotnie – dzięki temu nie ma cykli zależności. Ciągłość łuku nie jest ograniczeniem solvera, tylko wynikiem obliczeń przy rysowaniu.
- Dla łuku niedostępne jest C1, a między dwoma łukami – również G1 (każdy musiałby dopasować się do drugiego).
- Jeśli styczna sąsiada leży wzdłuż cięciwy, łuk degeneruje się do odcinka.

## 3. Model danych

```
geometry/           Vec2, odległości, test parzystości (zawieranie punktu), Bézier, łuki
model/              Scene, Polygon, Edge, EdgeShape, Vertex, ControlPoint, Continuity, DemoScene
model/constraints/  Constraint, FixedLength, Perpendicular, Smooth
solver/             Solver, Locks, Projections
editor/             Editor – trafianie, zaznaczanie, przeciąganie, komendy
render/             Painter, LibraryPainter, BresenhamPainter, bresenham()
ui/                 SceneCanvas, SidePanel, LengthDialog, HelpDialog
```

### Klasy

- `Point` – interfejs wszystkiego, co da się przesunąć (`pos: Vec2`). Implementują go `Vertex` i `ControlPoint`, więc solver traktuje je jednakowo.
- `Vertex` – pozycja, klasa ciągłości `continuity` (G0 / G1 / C1) i flaga `continuityExplicit` (klasa wybrana przez użytkownika, a nie domyślna).
- `Edge(start, end, shape)` – krawędź łącząca dwa wierzchołki. Krawędzie mają **tożsamość**: ograniczenia wskazują obiekty, nie indeksy, bo indeksy zmieniają się przy wstawianiu wierzchołków.
- `EdgeShape` – `Line`, `Bezier(c1, c2)` (c1 przy początku, c2 przy końcu) albo `Arc`. Kształt łuku nie jest przechowywany – wynika z końców krawędzi i ciągłości.
- `Polygon` – zamknięty pierścień krawędzi (`edges[i]` kończy się tam, gdzie zaczyna `edges[i+1]`). `polylineOf(edge)` zwraca łamaną krawędzi i jest **jedynym źródłem kształtu** dla rysowania, wypełnienia, trafiania kursorem i testu „wewnątrz”.
- `Scene` – lista wielokątów i lista ograniczeń. Ograniczenia są na poziomie sceny, więc mogą łączyć różne wielokąty. Scena pilnuje spójności: przy wstawianiu i usuwaniu wierzchołków oraz zmianie kształtu usuwa ograniczenia znikających krawędzi.
- `Constraint` – `FixedLength(edge, length)`, `Perpendicular(e1, e2)` i `Smooth(vertex, incoming, outgoing, c1)`. `Smooth` (G1/C1) **nie jest przechowywane** – przy każdym uruchomieniu solvera powstaje z `vertex.continuity` (`Polygon.continuityConstraints()`), dla wierzchołków przy krzywej Béziera.

### Stan i migawki

Jedynym stanem geometrii są pozycje punktów. Migawka sceny to mapa `punkt → pozycja` (`Scene.snapshot()`), a `restore` ją przywraca. Na tym opierają się transakcje: każda operacja, której solver nie potrafi dokończyć, jest wycofywana.

### Edytor

`Editor` zamienia akcje użytkownika na operacje na modelu – każda akcja to jedna metoda (`setLength`, `makePerpendicular`, `toBezier`, `toArc`, `setContinuity`, `deleteSelected`, `onPrimaryPress`, `onMove`…). Przechowuje zaznaczenie, element pod kursorem, stan przeciągania i licznik `version`, którego zmiana odświeża widok.

## 4. Algorytmy

### Rysowanie odcinków – Bresenham

Do wyboru w panelu: rysowanie biblioteczne (`drawLine`) albo własny algorytm Bresenhama (piksele rysowane przez `drawPoints`). Wybranym algorytmem rysowane są krawędzie, w tym łamane przybliżające krzywe Béziera i łuki. Elementy pomocnicze (wielobok kontrolny, uchwyty, ikony) zawsze rysowane są bibliotecznie.

Bresenham – wersja z punktem środkowym z wykładu:

- `d = 2·dy − dx`, krok E: `d += 2·dy`, krok NE: `d += 2·(dy − dx)`, wybór E gdy `d < 0`,
- 8 części okręgu: algorytm działa na wartościach bezwzględnych `dx`, `dy`, kierunek kroku pamiętają `sx`, `sy` (odbicie względem osi); dla `|dy| > |dx|` osie zamieniają się rolami (odbicie względem przekątnej).

### Krzywe Béziera – algorytm przyrostowy

Krzywa trzeciego stopnia V₀, V₁, V₂, V₃ jest przeliczana do **bazy potęgowej**, a punkty liczone **różnicami progresywnymi**:

- `A0 = V0`, `A1 = 3(V1 − V0)`, `A2 = 3(V2 − 2V1 + V0)`, `A3 = V3 − 3V2 + 3V1 − V0`,
- dla kroku `d = 1/n`: `Δ = A3·d³ + A2·d² + A1·d`, `Δ² = 6A3·d³ + 2A2·d²`, `Δ³ = 6A3·d³` (stała),
- pętla: `P += Δ`, `Δ += Δ²`, `Δ² += Δ³` – trzy dodawania wektorów na punkt, bez mnożeń,
- ostatni punkt to dokładnie V₃,
- liczba odcinków `n` – długość wieloboku kontrolnego / 4, w zakresie 1..200.

Kolejne punkty łączone są wybranym algorytmem rysowania odcinków.

### Łuki

- **G0 / G0**: środek w środku krawędzi, promień = połowa długości, kąt π.
- **G1 na początku V₀**: `t` – jednostkowa styczna sąsiada w V₀ (kierunek od jego punktu przed V₀ do V₀), `n` – wektor prostopadły do `t`, `d = V₁ − V₀`. Promień ze znakiem `r = (d·d) / (2·(d·n))`, środek `S = V₀ + n·r`. Okrąg jest styczny do `t` w V₀ i przechodzi przez V₁.
- **G1 na końcu**: ten sam wzór dla łuku odwróconego (od V₁ do V₀ ze styczną przeciwną).
- Łuk rysowany jest jako łamana (liczba odcinków zależna od kąta i promienia, 2..200). Końce są dokładne, więc wypełnienie, trafianie i test „wewnątrz” działają tak samo jak dla innych krawędzi.

### Solver ograniczeń

Solver działa przez **iteracyjne rzutowanie** (projekcje). Każde ograniczenie ma:

- `error()` – jak daleko jest od spełnienia,
- `project(locks)` – przesuwa swoje punkty tak, żeby błąd spadł do zera, z uwzględnieniem blokad.

```
solve(ograniczenia, blokady):
    powtórz do 200 razy:
        jeśli wszystkie error() < 0.001 → sukces
        dla każdego ograniczenia: project(blokady)
    sukces, jeśli wszystkie error() < 0.001
```

Solver dostaje ograniczenia zapisane w scenie oraz `Smooth` wyprowadzone z ciągłości wierzchołków.

#### Blokady

Każdy punkt ma poziom blokady, a ograniczenie przesuwa punkty o najniższym dostępnym poziomie:

1. **Locked** – przeciągany punkt (przy przeciąganiu krawędzi – wszystkie jej punkty). Nigdy się nie rusza, zawsze jest pod kursorem.
2. **Protected** – wierzchołki segmentu, którego punkt kontrolny jest przeciągany, oraz punkty kontrolne niesione razem z przeciąganym wierzchołkiem. Ruszają się tylko wtedy, gdy nie ma innego wyjścia.
3. **Free** – cała reszta.

#### Projekcje

- **Zadana długość odcinka**: końce przesuwają się wzdłuż krawędzi o `(|b − a| − L)`. Przy równych blokadach po połowie, inaczej rusza się tylko mniej zablokowany koniec.
- **Prostopadłość**: obraca się krawędź o mniej zablokowanych końcach – wokół bardziej zablokowanego końca albo wokół środka – do bliższego z dwóch kierunków prostopadłych do drugiej krawędzi. Obrót nie zmienia długości, więc nie psuje zadanej długości. Gdy wybrana krawędź jest całkowicie zablokowana, obraca się druga.
- **Ciągłość G1 / C1** w wierzchołku V: P – punkt tuż przed V, C – punkt tuż za V (sąsiedni wierzchołek odcinka albo punkt kontrolny Béziera), `u = V − P`, `w = C − V`.
  - G1: `w` ma kierunek `u` – C obraca się wokół V z zachowaniem długości ramienia (albo P, symetrycznie),
  - C1: `w = k·u`, gdzie `k = waga(wchodząca) / waga(wychodząca)`, waga Béziera 3, odcinka 1,
  - projekcja **nigdy nie rusza V** (przeciągany wierzchołek zostaje pod kursorem) i w pierwszej kolejności przesuwa punkt kontrolny, a nie daleki koniec odcinka.

#### Transakcje i fallback

- **Przeciąganie**: migawka → przesunięcie przeciąganych punktów (Locked) i niesionych punktów kontrolnych (Protected) → `solve`. Przy porażce migawka jest przywracana, a cały wielokąt przesuwany o ten sam wektor (fallback).
- **Dodanie ograniczenia**: migawka → dodanie → `solve` bez blokad. Przy porażce ograniczenie jest usuwane, pozycje przywracane, a w panelu pojawia się komunikat.
- **Zmiana ciągłości i zamiana na Bézier**: wierzchołki są zablokowane, więc krzywa wyrównuje się od razu przez ruch punktów kontrolnych. Przy porażce stan (pozycje i klasy) jest wycofywany.
- Scena demo po zbudowaniu jest raz rozwiązywana, więc startuje ze spełnionymi wszystkimi ograniczeniami.
