# Print reports for each test result
Dir.glob('turnstile/build/test-results/debug/*.xml') do |result|
    junit.parse result
    junit.report
end