# coding: utf-8
require 'spec_helper'

def topic_command(options)
  (['LOG_DIR=/var/log/kafka',
    '/opt/kafka/bin/kafka-topics.sh',
    '--zookeeper localhost:2181'] + options).join(' ')
end

describe "Kafka", :order => :defined do
  let(:sudo_options) { '-u kafka -i' }
  topic = 'serverspec-' + SecureRandom.hex(10)
  deleted = "#{topic} - marked for deletion"

  describe command(topic_command(['--create',
                                  '--replication-factor 1',
                                  '--partitions 1',
                                  "--topic #{topic}"])) do
    its(:stdout) { should contain("Created topic \"#{topic}\".") }
  end

  describe command(topic_command(['--list'])) do
    its(:stdout) { should contain(topic) }
    its(:stdout) { should_not contain(deleted) }
  end

  describe command(topic_command(['--delete',
                                  '--partitions 1',
                                  "--topic #{topic}"])) do
    its(:stdout) { should contain("Topic #{topic} is marked for deletion.") }
    its(:stdout) { should contain('Note: This will have no impact if delete.topic.enable is not set to true.') }
  end

  describe command(topic_command(['--list'])) do
    its(:stdout) { should contain(deleted) }
  end
end
