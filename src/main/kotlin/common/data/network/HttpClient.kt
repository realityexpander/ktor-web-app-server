package common.data.network

/**
 * HttpClient is a data holder class for transferring data to/from the Domain from API.<br></br>
 *
 * This is a placeholder for a real HttpClient class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */
class HttpClient {
    private val client: String

    constructor(client: String) {
        this.client = client
    }

    constructor() {
        client = "Mozilla/5.0"
    }
}