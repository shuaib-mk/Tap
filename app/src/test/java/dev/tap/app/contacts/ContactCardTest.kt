package dev.tap.app.contacts

import org.junit.Assert.*
import org.junit.Test

class ContactCardTest {

    @Test
    fun testDefaultContactCardGeneration() {
        val card = ContactCard.createDefault()
        assertNotNull(card.id)
        assertTrue(card.displayName.isNotBlank())
        assertTrue(card.phone.isNotBlank())
        assertTrue(card.email.isNotBlank())

        val vcard = card.toVCard3(filterSelected = false)
        assertTrue("vCard must start with BEGIN:VCARD", vcard.startsWith("BEGIN:VCARD"))
        assertTrue("vCard must end with END:VCARD", vcard.trim().endsWith("END:VCARD"))
        assertTrue("vCard must specify VERSION:3.0", vcard.contains("VERSION:3.0"))
        assertTrue("vCard must contain FN line", vcard.contains("FN:${card.displayName}"))
        assertTrue("vCard must contain TEL line", vcard.contains("TEL;TYPE=CELL,VOICE:${card.phone}"))
        assertTrue("vCard must contain EMAIL line", vcard.contains("EMAIL;TYPE=INTERNET,PREF:${card.email}"))
    }

    @Test
    fun testVCardFieldPrivacyFiltering() {
        val card = ContactCard.createDefault()

        // Exclude phone and email
        val filtered = card
            .toggleField(ContactCard.FIELD_PHONE)
            .toggleField(ContactCard.FIELD_EMAIL)

        val vcard = filtered.toVCard3(filterSelected = true)

        assertFalse("Phone must not be included when filtered", vcard.contains("TEL;"))
        assertFalse("Email must not be included when filtered", vcard.contains("EMAIL;"))
        assertTrue("Display name must still be included", vcard.contains("FN:${card.displayName}"))
    }

    @Test
    fun testVCard4Generation() {
        val card = ContactCard(
            displayName = "Jane Doe",
            jobTitle = "Lead Architect",
            company = "Acme Inc",
            phone = "+1234567890",
            email = "jane@acme.com",
            website = "https://acme.com"
        )
        val vcard4 = card.toVCard4(filterSelected = false)
        assertTrue(vcard4.contains("VERSION:4.0"))
        assertTrue(vcard4.contains("FN:Jane Doe"))
        assertTrue(vcard4.contains("TITLE:Lead Architect"))
        assertTrue(vcard4.contains("ORG:Acme Inc"))
        assertTrue(vcard4.contains("VALUE=uri:tel:+1234567890"))
    }

    @Test
    fun testVCardParsing() {
        val rawVCard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Johnathan Appleseed
            ORG:Fruit Tech Corp
            TITLE:VP of Innovation
            TEL;TYPE=CELL,VOICE:+1-800-555-0199
            EMAIL;TYPE=INTERNET,PREF:jappleseed@fruit.com
            URL:https://fruit.com
            NOTE:Met at tech conference
            END:VCARD
        """.trimIndent()

        val parsed = ContactCard.parseVCard(rawVCard)
        assertEquals("Johnathan Appleseed", parsed.displayName)
        assertEquals("VP of Innovation", parsed.jobTitle)
        assertEquals("Fruit Tech Corp", parsed.company)
        assertEquals("+1-800-555-0199", parsed.phone)
        assertEquals("jappleseed@fruit.com", parsed.email)
        assertEquals("https://fruit.com", parsed.website)
        assertEquals("Met at tech conference", parsed.notes)
    }

    @Test
    fun testFieldToggleImmutability() {
        val card = ContactCard.createDefault()
        assertTrue(card.isFieldSelected(ContactCard.FIELD_PHONE))

        val toggled = card.toggleField(ContactCard.FIELD_PHONE)
        assertFalse(toggled.isFieldSelected(ContactCard.FIELD_PHONE))
        // Original instance remains unmodified
        assertTrue(card.isFieldSelected(ContactCard.FIELD_PHONE))
    }
}
