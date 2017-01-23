# Print reports for each test result
Dir.glob('turnstile/build/test-results/release/*.xml') do |result|
    junit.parse result
    junit.report
end