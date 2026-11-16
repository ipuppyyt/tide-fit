package com.artishorizon.tidefit.feature

import com.artishorizon.tidefit.domain.model.HeightUnit
import com.artishorizon.tidefit.domain.model.WeightUnit
import com.artishorizon.tidefit.feature.profile.form.FormSection
import com.artishorizon.tidefit.feature.profile.form.ProfileForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ProfileFormTest {
    private val valid = ProfileForm(
        name = " Asha ", age = "29", heightCm = "165",
        initialWeight = "80", currentWeight = "76,5", targetWeight = "68"
    )

    @Test fun `valid form converts and trims`() {
        val p = valid.toProfile(Instant.EPOCH)!!
        assertEquals("Asha", p.name)
        assertEquals(76.5, p.currentWeightKg, 1e-9) // comma decimal accepted
    }

    @Test fun `blank name and bad age are rejected`() {
        assertNotNull(valid.copy(name = " ").validate(FormSection.BASICS))
        assertNotNull(valid.copy(age = "7").validate(FormSection.BASICS))
        assertNotNull(valid.copy(age = "").validate(FormSection.BASICS))
        assertNull(valid.validate(FormSection.BASICS))
    }

    @Test fun `invalid body values are rejected`() {
        assertNotNull(valid.copy(heightCm = "5").validate(FormSection.BODY))
        assertNotNull(valid.copy(currentWeight = "-3").validate(FormSection.BODY))
        assertNotNull(valid.copy(targetWeight = "abc").validate(FormSection.BODY))
        assertNotNull(valid.copy(initialWeight = "NaN").validate(FormSection.BODY))
        assertNull(valid.copy(currentWeight = "0").toProfile(Instant.EPOCH))
    }

    @Test fun `pounds and feet inches are converted to metric`() {
        val form = valid.copy(
            weightUnit = WeightUnit.LB, heightUnit = HeightUnit.FT_IN,
            heightFeet = "5", heightInches = "10",
            initialWeight = "180", currentWeight = "170", targetWeight = "150"
        )
        val p = form.toProfile(Instant.EPOCH)!!
        assertEquals(177.8, p.heightCm, 1e-9)
        assertEquals(77.1107, p.currentWeightKg, 1e-3)
    }

    @Test fun `inches of 12 or more are invalid`() {
        val f = valid.copy(heightUnit = HeightUnit.FT_IN, heightFeet = "5", heightInches = "12")
        assertNotNull(f.validate(FormSection.BODY))
    }

    @Test fun `free text allergies are merged and round trip`() {
        val p = valid.copy(allergies = setOf("Peanuts"), allergiesOther = "kiwi; sesame ").toProfile(Instant.EPOCH)!!
        assertEquals(setOf("Peanuts", "kiwi", "sesame"), p.allergies.toSet())
        val back = ProfileForm.fromProfile(p)
        assertEquals(setOf("Peanuts"), back.allergies)
        assertEquals("kiwi, sesame", back.allergiesOther)
    }
}
