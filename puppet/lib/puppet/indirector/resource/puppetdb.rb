require 'puppet/indirector/rest'
require 'puppet/util/puppetdb'

class Puppet::Resource::Puppetdb < Puppet::Indirector::REST
  include Puppet::Util::Puppetdb

  def search(request)
    type   = request.key
    host   = request.options[:host]
    filter = request.options[:filter]
    scope  = request.options[:scope]

    # At minimum, we want to filter to the right type of exported resources.
    expr = ['and',
             ['=', 'type', type],
             ['=', 'exported', true],
             ['=', ['node', 'active'], true],
             ['not',
               ['=', ['node', 'name'], host]]]

    filter_expr = build_expression(filter)
    expr << filter_expr if filter_expr

    query_string = "query=#{expr.to_pson}"

    begin
      response = http_get(request, "/resources?#{query_string}", headers)

      unless response.is_a? Net::HTTPSuccess
        # Newline characters cause an HTTP error, so strip them
        raise "[#{response.code} #{response.message}] #{response.body.gsub(/[\r\n]/, '')}"
      end
    rescue => e
      raise Puppet::Error, "Could not retrieve resources from the PuppetDB at #{self.class.server}:#{self.class.port}: #{e}"
    end

    resources = PSON.load(response.body)
    resources.map do |res|
      params = res['parameters'] || {}
      params = params.map do |name,value|
        Puppet::Parser::Resource::Param.new(:name => name, :value => value)
      end
      attrs = {:parameters => params, :scope => scope}
      Puppet::Parser::Resource.new(res['type'], res['title'], attrs)
    end
  end

  def build_expression(filter)
    return nil unless filter

    lhs, op, rhs = filter

    case op
    when '==', '!='
      build_predicate(op, lhs, rhs)
    when 'and', 'or'
      build_join(op, lhs, rhs)
    else
      raise Puppet::Error, "Operator #{op} in #{filter.inspect} not supported"
    end
  end

  def build_predicate(op, field, value)
    # Title and tag aren't parameters, so we have to special-case them.
    path = case field
           when "tag", "title"
             field
           else
             ['parameter', field]
           end

    equal_expr = ['=', path, value]

    op == '!=' ? ['not', equal_expr] : equal_expr
  end

  def build_join(op, lhs, rhs)
    lhs = build_expression(lhs)
    rhs = build_expression(rhs)

    [op, lhs, rhs]
  end

  def headers
    {'Accept' => 'application/json'}
  end
end
