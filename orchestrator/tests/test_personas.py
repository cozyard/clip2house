from app.personas import load_personas


def test_같은_시드는_같은_페르소나_집합을_돌려준다():
    first = load_personas(count=24, seed=42)
    second = load_personas(count=24, seed=42)
    assert [p.id for p in first] == [p.id for p in second]
    assert len(first) == 24


def test_층화_추출은_성별과_연령대를_섞는다():
    picked = load_personas(count=24, seed=42)
    genders = {p.gender for p in picked}
    age_bands = {p.age_band for p in picked}
    assert genders == {"M", "F"}
    assert len(age_bands) >= 4


def test_quiet_window는_전역_범위_안에서_성격을_반영한다():
    personas = load_personas(count=32, seed=1)
    impatient = min(personas, key=lambda p: p.patience)
    patient = max(personas, key=lambda p: p.patience)

    low_i, high_i = impatient.quiet_window_s(8.0, 90.0)
    low_p, high_p = patient.quiet_window_s(8.0, 90.0)

    assert 8.0 <= low_i < high_i <= 90.0
    assert 8.0 <= low_p < high_p <= 90.0
    assert low_p > low_i
