package contracts.beer.rest

import org.springframework.cloud.contract.spec.Contract

Contract.make {
	priority 1
	request {
		method 'POST'
		url '/stats'
		body(
				name: "bizarre"
		)
		headers {
			contentType(applicationJson())
		}
	}
	response {
		status 409
		body(
				quantity: 0,
				text: "WAT?"
		)
		headers {
			contentType(applicationJson())
		}
	}
}
