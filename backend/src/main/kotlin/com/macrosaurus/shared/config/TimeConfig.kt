package com.macrosaurus.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
internal class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
